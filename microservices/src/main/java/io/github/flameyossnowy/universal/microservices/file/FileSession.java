package io.github.flameyossnowy.universal.microservices.file;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.cache.graph.DefaultGraphCoordinator;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance file-based database session with batch operation support.
 *
 * Supported {@link SessionOption}s:
 * <ul>
 *   <li>{@code NO_CACHE}        – bypass the in-memory cache on every read</li>
 *   <li>{@code BUFFERED_WRITE}  – defer all writes until {@link #commit()} is called</li>
 *   <li>{@code LOG_OPERATIONS}  – log every INSERT / UPDATE / DELETE via {@link Logging}</li>
 *   <li>{@code CASCADE}         – propagate writes through relationship graphs</li>
 * </ul>
 *
 * @param <T>  The entity type
 * @param <ID> The ID type
 */
public class FileSession<T, ID> implements DatabaseSession<ID, T, FileContext> {
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(0);

    private final long sessionId;
    private final FileRepositoryAdapter<T, ID> adapter;
    private final EnumSet<SessionOption> options;
    private final SessionCache<ID, T> cache;

    // Pending operations for BUFFERED_WRITE mode
    private final List<Runnable> pendingOperations = new ArrayList<>(8);
    private final List<Runnable> rollbackCallbacks = new ArrayList<>(8);
    private final List<TransactionResult<?>> results = new ArrayList<>(8);

    private final Map<ID, T> insertBatch = new ConcurrentHashMap<>(5);
    private final Map<ID, T> updateBatch = new ConcurrentHashMap<>(5);
    private final List<ID> deleteBatch = new ArrayList<>(5);
    private final int batchSize;
    private boolean closed = false;
    private boolean autoFlush;

    // Non-cascading view passed to graph coordinator to avoid infinite recursion
    private final DatabaseSession<ID, T, FileContext> nonCascadingView = new DatabaseSession<>() {
        @Override public SessionCache<ID, T> getCache()          { return FileSession.this.getCache(); }
        @Override public long getId()                             { return FileSession.this.getId(); }
        @Override public void rollback()                          { FileSession.this.rollback(); }
        @Override public boolean insert(T entity)                 { return FileSession.this.insertDirect(entity); }
        @Override public boolean delete(T entity)                 { return FileSession.this.deleteDirect(entity); }
        @Override public boolean update(T entity)                 { return FileSession.this.updateDirect(entity); }
        @Override public void close()                             { FileSession.this.close(); }
        @Override public T findById(ID key)                       { return FileSession.this.findById(key); }
        @Override public TransactionResult<Boolean> commit()      { return FileSession.this.commit(); }
        @Override public FileContext connection()                  { return FileSession.this.connection(); }
    };

    public FileSession(FileRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options) {
        this(adapter, options, DEFAULT_BATCH_SIZE, true);
    }

    public FileSession(FileRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options,
                       int batchSize, boolean autoFlush) {
        this.sessionId  = SESSION_ID_GENERATOR.incrementAndGet();
        this.adapter    = adapter;
        this.options    = options != null ? options : EnumSet.noneOf(SessionOption.class);
        this.cache      = new FileSessionCache<>();
        this.batchSize  = Math.max(1, batchSize);
        this.autoFlush  = autoFlush;
    }

    private boolean noCache()       { return options.contains(SessionOption.NO_CACHE); }
    private boolean buffered()      { return options.contains(SessionOption.BUFFERED_WRITE); }
    private boolean logOps()        { return options.contains(SessionOption.LOG_OPERATIONS); }
    private boolean cascade()       { return options.contains(SessionOption.CASCADE); }

    private void log(String message) {
        Logging.info(() -> "[FileSession " + sessionId + "] " + message);
    }

    @Override
    public SessionCache<ID, T> getCache() {
        checkClosed();
        return cache;
    }

    @Override
    public long getId() {
        return sessionId;
    }

    @Override
    public T findById(ID key) {
        checkClosed();

        if (!noCache()) {
            T cached = cache.get(key);
            if (cached != null) return cached;

            T batched = insertBatch.get(key);
            if (batched == null) batched = updateBatch.get(key);
            if (batched != null) {
                cache.put(key, batched);
                return batched;
            }
        }

        T entity = adapter.findById(key);
        if (entity != null && !noCache()) cache.put(key, entity);
        return entity;
    }

    @Override
    public boolean insert(T entity) {
        checkClosed();
        if (cascade() && adapter.getRepositoryModel().hasRelationships()) {
            DefaultGraphCoordinator.of(adapter.getRepositoryModel())
                .cascadeInsert(entity, nonCascadingView);
            return true;
        }
        return insertDirect(entity);
    }

    private boolean insertDirect(T entity) {
        ID id = adapter.extractId(entity);

        if (logOps()) log("INSERT " + entity);

        Runnable operation = () -> {
            try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
                TransactionResult<Boolean> result = adapter.insert(entity, tx);
                results.add(result);
                if (result.getResult().orElse(Boolean.FALSE)) {
                    if (!noCache()) cache.put(id, entity);
                    tx.commit();
                }
            } catch (Exception e) {
                results.add(TransactionResult.failure(e));
            }
        };

        if (buffered()) {
            insertBatch.put(id, entity);
            if (!noCache()) cache.put(id, entity);
            pendingOperations.add(operation);
        } else {
            insertBatch.put(id, entity);
            if (!noCache()) cache.put(id, entity);
            if (autoFlush && insertBatch.size() >= batchSize) flushInserts();
        }

        rollbackCallbacks.add(() -> cache.remove(id));
        return true;
    }

    @Override
    public boolean update(T entity) {
        checkClosed();
        if (cascade() && adapter.getRepositoryModel().hasRelationships()) {
            DefaultGraphCoordinator.of(adapter.getRepositoryModel())
                .cascadeUpdate(entity, nonCascadingView);
            return true;
        }
        return updateDirect(entity);
    }

    private boolean updateDirect(T entity) {
        ID id = adapter.extractId(entity);
        T previous = noCache() ? null : findById(id);

        if (logOps()) log("UPDATE " + entity);

        Runnable operation = () -> {
            try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
                TransactionResult<Boolean> result = adapter.updateAll(entity, tx);
                results.add(result);
                if (result.getResult().orElse(Boolean.FALSE)) {
                    if (!noCache()) cache.put(id, entity);
                    tx.commit();
                }
            } catch (Exception e) {
                results.add(TransactionResult.failure(e));
            }
        };

        if (buffered()) {
            updateBatch.put(id, entity);
            if (!noCache()) cache.put(id, entity);
            pendingOperations.add(operation);
        } else {
            // If already staged as an insert, just update that staging entry
            if (insertBatch.containsKey(id)) {
                insertBatch.put(id, entity);
            } else {
                updateBatch.put(id, entity);
                if (autoFlush && updateBatch.size() >= batchSize) flushUpdates();
            }
            if (!noCache()) cache.put(id, entity);
        }

        rollbackCallbacks.add(() -> {
            if (previous != null) cache.put(id, previous);
        });
        return true;
    }

    @Override
    public boolean delete(T entity) {
        checkClosed();
        if (cascade() && adapter.getRepositoryModel().hasRelationships()) {
            DefaultGraphCoordinator.of(adapter.getRepositoryModel())
                .cascadeDelete(entity, nonCascadingView);
            return true;
        }
        return deleteDirect(entity);
    }

    private boolean deleteDirect(T entity) {
        ID id = adapter.extractId(entity);
        T previous = noCache() ? null : cache.get(id);

        if (logOps()) log("DELETE " + entity);

        Runnable operation = () -> {
            try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
                TransactionResult<Boolean> result = adapter.delete(entity, tx);
                results.add(result);
                if (result.getResult().orElse(Boolean.FALSE)) {
                    cache.remove(id);
                    tx.commit();
                }
            } catch (Exception e) {
                results.add(TransactionResult.failure(e));
            }
        };

        insertBatch.remove(id);
        updateBatch.remove(id);
        cache.remove(id);

        if (buffered()) {
            deleteBatch.add(id);
            pendingOperations.add(operation);
        } else {
            deleteBatch.add(id);
            if (autoFlush && deleteBatch.size() >= batchSize) flushDeletes();
        }

        rollbackCallbacks.add(() -> {
            if (previous != null) cache.put(id, previous);
        });
        return true;
    }

    @Override
    public TransactionResult<Boolean> commit() {
        checkClosed();

        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            if (buffered()) {
                // Run all deferred operations now
                for (Runnable action : pendingOperations) {
                    action.run();
                }
            } else {
                flushInserts();
                flushUpdates();
                flushDeletes();
            }

            // Check for any errors collected during operations
            for (TransactionResult<?> result : results) {
                if (result.isError()) {
                    rollback();
                    return TransactionResult.failure(
                        result.getError().orElse(new IllegalStateException("Operation failed"))
                    );
                }
            }

            TransactionResult<Boolean> txResult = tx.commit();
            if (!txResult.isError()) {
                pendingOperations.clear();
                rollbackCallbacks.clear();
                results.clear();
            }
            return txResult;

        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public void rollback() {
        rollbackCallbacks.forEach(Runnable::run);
        pendingOperations.clear();
        rollbackCallbacks.clear();
        results.clear();
        insertBatch.clear();
        updateBatch.clear();
        deleteBatch.clear();
        cache.clear();
    }

    public void flushInserts() {
        if (insertBatch.isEmpty()) return;
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            adapter.insertAll(insertBatch.values(), tx).ifError(e -> { throw new RuntimeException(e); });
            tx.commit();
            insertBatch.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush inserts", e);
        }
    }

    public void flushUpdates() {
        if (updateBatch.isEmpty()) return;
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            for (T entity : updateBatch.values()) {
                adapter.updateAll(entity, tx).ifError(e -> { throw new RuntimeException(e); });
            }
            tx.commit();
            updateBatch.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush updates", e);
        }
    }

    public void flushDeletes() {
        if (deleteBatch.isEmpty()) return;
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            for (ID id : deleteBatch) {
                adapter.deleteById(id, tx).ifError(e -> { throw new RuntimeException(e); });
            }
            tx.commit();
            deleteBatch.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush deletes", e);
        }
    }

    public void flush() {
        checkClosed();
        flushInserts();
        flushUpdates();
        flushDeletes();
    }

    @Override
    public boolean insertAll(Iterable<T> entities) {
        checkClosed();
        boolean ok = true;
        for (T e : entities) ok &= insert(e);
        return ok;
    }

    @Override
    public boolean updateAll(Iterable<T> entities) {
        checkClosed();
        boolean ok = true;
        for (T e : entities) ok &= update(e);
        return ok;
    }

    @Override
    public boolean deleteAll(Iterable<T> entities) {
        checkClosed();
        boolean ok = true;
        for (T e : entities) ok &= delete(e);
        return ok;
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> ids) {
        checkClosed();
        Map<ID, T> result = new ConcurrentHashMap<>(ids.size());
        List<ID> missing  = new ArrayList<>(ids.size());

        for (ID id : ids) {
            T entity = findById(id);
            if (entity != null) result.put(id, entity);
            else missing.add(id);
        }

        if (!missing.isEmpty()) {
            Map<ID, T> batch = adapter.findAllById(missing);
            result.putAll(batch);
            if (!noCache()) batch.forEach(cache::put);
        }

        return result;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                if (autoFlush && !buffered()) flush();
            } finally {
                rollback();
                closed = true;
            }
        }
    }

    @Override
    public FileContext connection() {
        checkClosed();
        return new FileContext(adapter.getEntityStore().basePath(), true);
    }

    public void setAutoFlush(boolean autoFlush)  { this.autoFlush = autoFlush; }
    public int  getBatchSize()                    { return batchSize; }
    public int  getPendingOperationCount()        { return pendingOperations.size() + insertBatch.size() + updateBatch.size() + deleteBatch.size(); }

    private void checkClosed() {
        if (closed) throw new IllegalStateException("Session is closed");
    }
}