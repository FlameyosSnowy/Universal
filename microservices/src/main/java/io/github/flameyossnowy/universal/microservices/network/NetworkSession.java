package io.github.flameyossnowy.universal.microservices.network;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.cache.graph.DefaultGraphCoordinator;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance network-based database session with batch operation support.
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
public class NetworkSession<T, ID> implements DatabaseSession<ID, T, HttpClient> {
    private static final int DEFAULT_BATCH_SIZE            = 100;
    private static final int DEFAULT_MAX_RETRIES           = 3;
    private static final int DEFAULT_MAX_CONCURRENT        = 16;
    private static final AtomicLong SESSION_ID_GENERATOR   = new AtomicLong(0);

    private final long sessionId;
    private final NetworkRepositoryAdapter<T, ID> adapter;
    private final EnumSet<SessionOption> options;
    private final SessionCache<ID, T> cache;
    private final ExecutorService executorService;

    // Deduplication of in-flight fetches
    private final Map<ID, CompletableFuture<T>> pendingFutures = new ConcurrentHashMap<>(3);

    // Write staging
    private final Map<ID, T>  insertBatch  = new ConcurrentHashMap<>(3);
    private final Map<ID, T>  updateBatch  = new ConcurrentHashMap<>(3);
    private final Set<ID>     deleteBatch  = ConcurrentHashMap.newKeySet();

    // BUFFERED_WRITE deferred operations
    private final List<Runnable>              pendingOperations = new ArrayList<>(8);
    private final List<Runnable>              rollbackCallbacks = new ArrayList<>(8);
    private final List<TransactionResult<?>>  results           = new ArrayList<>(8);

    private final int     batchSize;
    private final int     maxRetries;
    private final int     maxConcurrentRequests;
    private boolean       closed    = false;
    private boolean       autoFlush = true;

    // Non-cascading view passed to graph coordinator to avoid infinite recursion
    private final DatabaseSession<ID, T, HttpClient> nonCascadingView = new DatabaseSession<>() {
        @Override public SessionCache<ID, T> getCache()         { return NetworkSession.this.getCache(); }
        @Override public long getId()                            { return NetworkSession.this.getId(); }
        @Override public void rollback()                         { NetworkSession.this.rollback(); }
        @Override public boolean insert(T entity)                { return NetworkSession.this.insertDirect(entity); }
        @Override public boolean delete(T entity)                { return NetworkSession.this.deleteDirect(entity); }
        @Override public boolean update(T entity)                { return NetworkSession.this.updateDirect(entity); }
        @Override public void close()                            { NetworkSession.this.close(); }
        @Override public T findById(ID key)                      { return NetworkSession.this.findById(key); }
        @Override public TransactionResult<Boolean> commit()     { return NetworkSession.this.commit(); }
        @Override public HttpClient connection()                  { return NetworkSession.this.connection(); }
    };

    public NetworkSession(NetworkRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options) {
        this(adapter, options, DEFAULT_BATCH_SIZE, DEFAULT_MAX_RETRIES, DEFAULT_MAX_CONCURRENT);
    }

    public NetworkSession(NetworkRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options,
                          int batchSize, int maxRetries, int maxConcurrentRequests) {
        this.sessionId             = SESSION_ID_GENERATOR.incrementAndGet();
        this.adapter               = adapter;
        this.options               = options != null ? options : EnumSet.noneOf(SessionOption.class);
        this.cache                 = new NetworkSessionCache<>();
        this.batchSize             = Math.max(1, batchSize);
        this.maxRetries            = Math.max(0, maxRetries);
        this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        this.executorService       = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), this.maxConcurrentRequests)
        );
    }

    private boolean noCache()  { return options.contains(SessionOption.NO_CACHE); }
    private boolean buffered() { return options.contains(SessionOption.BUFFERED_WRITE); }
    private boolean logOps()   { return options.contains(SessionOption.LOG_OPERATIONS); }
    private boolean cascade()  { return options.contains(SessionOption.CASCADE); }

    private void log(String message) {
        Logging.info(() -> "[NetworkSession " + sessionId + "] " + message);
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

            // Check if another thread is already fetching this
            CompletableFuture<T> inflight = pendingFutures.get(key);
            if (inflight != null) {
                try { return inflight.get(); }
                catch (Exception e) { throw new RuntimeException("Failed to fetch entity: " + key, e); }
            }
        }

        return fetchFromNetwork(key);
    }

    private T fetchFromNetwork(ID key) {
        CompletableFuture<T> future   = new CompletableFuture<>();
        CompletableFuture<T> existing = pendingFutures.putIfAbsent(key, future);

        if (existing != null) {
            try { return existing.get(); }
            catch (Exception e) { throw new RuntimeException("Failed to fetch entity: " + key, e); }
        }

        try {
            T result = withRetry(() -> adapter.findById(key), maxRetries);
            future.complete(result);
            if (result != null && !noCache()) cache.put(key, result);
            return result;
        } catch (Exception e) {
            future.completeExceptionally(e);
            throw new RuntimeException("Failed to fetch entity: " + key, e);
        } finally {
            pendingFutures.remove(key, future);
        }
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
            try (NetworkTransactionContext tx = new NetworkTransactionContext(
                adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {
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

        deleteBatch.remove(id);
        insertBatch.put(id, entity);
        if (!noCache()) cache.put(id, entity);

        if (buffered()) {
            pendingOperations.add(operation);
        } else {
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
        ID id       = adapter.extractId(entity);
        T  previous = noCache() ? null : cache.get(id);

        if (logOps()) log("UPDATE " + entity);

        Runnable operation = () -> {
            try (NetworkTransactionContext tx = new NetworkTransactionContext(
                adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {
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

        deleteBatch.remove(id);
        if (!noCache()) cache.put(id, entity);

        if (buffered()) {
            updateBatch.put(id, entity);
            pendingOperations.add(operation);
        } else {
            if (insertBatch.containsKey(id)) {
                insertBatch.put(id, entity); // update the staged insert in-place
            } else {
                updateBatch.put(id, entity);
                if (autoFlush && updateBatch.size() >= batchSize) flushUpdates();
            }
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
        ID id       = adapter.extractId(entity);
        T  previous = noCache() ? null : cache.get(id);

        if (logOps()) log("DELETE " + entity);

        Runnable operation = () -> {
            try (NetworkTransactionContext tx = new NetworkTransactionContext(
                adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {
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
        deleteBatch.add(id);

        if (buffered()) {
            pendingOperations.add(operation);
        } else {
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

        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {

            if (buffered()) {
                for (Runnable action : pendingOperations) action.run();
            } else {
                flushInserts(tx);
                flushUpdates(tx);
                flushDeletes(tx);
            }

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
        pendingFutures.clear();
        cache.clear();
    }

    public void flushInserts() {
        if (insertBatch.isEmpty()) return;
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {
            flushInserts(tx);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush inserts", e);
        }
    }

    private void flushInserts(NetworkTransactionContext tx) {
        if (insertBatch.isEmpty()) return;
        try {
            List<T> batch = new ArrayList<>(insertBatch.values());
            insertBatch.clear();
            withRetry(() -> {
                adapter.insertAll(batch, tx).ifError(e -> { throw new RuntimeException(e); });
                return (Void) null;
            }, maxRetries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush inserts", e);
        }
    }

    public void flushUpdates() {
        if (updateBatch.isEmpty()) return;
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {
            flushUpdates(tx);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush updates", e);
        }
    }

    private void flushUpdates(NetworkTransactionContext tx) {
        if (updateBatch.isEmpty()) return;
        try {
            List<T> batch = new ArrayList<>(updateBatch.values());
            updateBatch.clear();
            for (T entity : batch) {
                withRetry(() -> {
                    adapter.updateAll(entity, tx).ifError(e -> { throw new RuntimeException(e); });
                    return (Void) null;
                }, maxRetries);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush updates", e);
        }
    }

    public void flushDeletes() {
        if (deleteBatch.isEmpty()) return;
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), batchSize, maxConcurrentRequests)) {
            flushDeletes(tx);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush deletes", e);
        }
    }

    private void flushDeletes(NetworkTransactionContext tx) {
        if (deleteBatch.isEmpty()) return;
        try {
            List<ID> batch = new ArrayList<>(deleteBatch);
            deleteBatch.clear();
            for (ID id : batch) {
                withRetry(() -> {
                    adapter.deleteById(id, tx).ifError(e -> { throw new RuntimeException(e); });
                    return (Void) null;
                }, maxRetries);
            }
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
        Map<ID, T> result  = new ConcurrentHashMap<>(ids.size());
        List<ID>   missing = new ArrayList<>(ids.size());

        for (ID id : ids) {
            T entity = findById(id);
            if (entity != null) result.put(id, entity);
            else missing.add(id);
        }

        if (!missing.isEmpty()) {
            try {
                Map<ID, T> batch = withRetry(() -> adapter.findAllById(missing), maxRetries);
                batch.forEach((id, entity) -> {
                    if (!noCache()) cache.put(id, entity);
                    result.put(id, entity);
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to batch-fetch entities", e);
            }
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
                executorService.shutdown();
            }
        }
    }

    @Override
    public HttpClient connection() {
        checkClosed();
        return adapter.getHttpClient();
    }

    public void setAutoFlush(boolean autoFlush)  { this.autoFlush = autoFlush; }
    public int  getBatchSize()                    { return batchSize; }
    public int  getPendingOperationCount()        { return pendingOperations.size() + insertBatch.size() + updateBatch.size() + deleteBatch.size() + pendingFutures.size(); }

    private static <R> R withRetry(ThrowingSupplier<R> task, int maxRetries) throws Exception {
        int attempts = 0;
        Exception lastError = null;
        while (attempts <= maxRetries) {
            try {
                return task.get();
            } catch (Exception e) {
                lastError = e;
                if (attempts == maxRetries) break;
                try { Thread.sleep((long) Math.pow(2, attempts) * 100); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation interrupted", ie);
                }
                attempts++;
            }
        }
        throw lastError != null ? lastError : new RuntimeException("Operation failed after " + maxRetries + " retries");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<R> {
        R get() throws Exception;
    }

    private void checkClosed() {
        if (closed) throw new IllegalStateException("Session is closed");
    }
}