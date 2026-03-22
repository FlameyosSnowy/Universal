package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.cache.graph.DefaultGraphCoordinator;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DefaultSession<ID, T, C> implements DatabaseSession<ID, T, C> {
    private final SessionCache<ID, T> cache;
    private final RepositoryAdapter<T, ID, C> repository;
    private final TransactionContext<C> transactionContext;
    private final RepositoryModel<T, ID> information;
    private final long id;
    private final EnumSet<SessionOption> options;

    private final List<Runnable> pendingOperations = new ArrayList<>(5);
    private final List<Runnable> rollbackCallbacks = new ArrayList<>(5);
    private final List<TransactionResult<?>> results = new ArrayList<>(5);

    private final DatabaseSession<ID, T, C> nonCascadingView = new DatabaseSession<>() {
        @Override public SessionCache<ID, T> getCache()         { return DefaultSession.this.getCache(); }
        @Override public long getId()                            { return DefaultSession.this.getId(); }
        @Override public void rollback()                         { DefaultSession.this.rollback(); }
        @Override public boolean insert(T entity)                { return DefaultSession.this.insertDirect(entity); }
        @Override public boolean delete(T entity)                { return DefaultSession.this.deleteDirect(entity); }
        @Override public boolean update(T entity)                { return DefaultSession.this.updateDirect(entity); }
        @Override public void close()                            { DefaultSession.this.close(); }
        @Override public T findById(ID key)                      { return DefaultSession.this.findById(key); }
        @Override public TransactionResult<Boolean> commit()     { return DefaultSession.this.commit(); }
        @Override public C connection()                          { return DefaultSession.this.connection(); }
    };

    public DefaultSession(RepositoryAdapter<T, ID, C> repository, SessionCache<ID, T> cache, long id, EnumSet<SessionOption> options) {
        this.repository         = repository;
        this.transactionContext = repository.beginTransaction();
        this.cache              = cache;
        this.information        = repository.getRepositoryModel();
        this.id                 = id;
        this.options            = options != null ? options : EnumSet.noneOf(SessionOption.class);
    }

    private boolean noCache()  { return options.contains(SessionOption.NO_CACHE); }
    private boolean buffered() { return options.contains(SessionOption.BUFFERED_WRITE); }
    private boolean logOps()   { return options.contains(SessionOption.LOG_OPERATIONS); }
    private boolean cascade()  { return options.contains(SessionOption.CASCADE); }

    @Override
    public SessionCache<ID, T> getCache() { return cache; }

    @Override
    public long getId() { return id; }

    @Override
    public void close() {
        transactionContext.close();
        cache.clear();
    }

    @Override
    public C connection() { return transactionContext.connection(); }

    @Override
    public T findById(ID key) {
        return noCache()
            ? repository.findById(key)
            : cache.computeIfAbsent(key, repository::findById);
    }

    /**
     * Finds multiple entities by their IDs.
     * Cache-hits are returned immediately; remaining IDs are fetched from the
     * repository in a single batch call via {@link RepositoryAdapter#findAllById}.
     *
     * @param ids the IDs to look up
     * @return a map of ID -> entity for every ID that was found; missing IDs are absent
     */
    @Override
    public Map<ID, T> findAllById(Collection<ID> ids) {
        Map<ID, T> result  = new LinkedHashMap<>(ids.size());
        List<ID>   missing = new ArrayList<>(ids.size());

        for (ID key : ids) {
            if (!noCache()) {
                T cached = cache.get(key);
                if (cached != null) {
                    result.put(key, cached);
                    continue;
                }
            }
            missing.add(key);
        }

        if (!missing.isEmpty()) {
            Map<ID, T> fetched = repository.findAllById(missing);
            fetched.forEach((key, entity) -> {
                if (!noCache()) cache.put(key, entity);
                result.put(key, entity);
            });
        }

        return result;
    }

    /**
     * Async variant of {@link #findAllById(Collection)}.
     */
    @Override
    public CompletableFuture<Map<ID, T>> findAllByIdAsync(Collection<ID> ids) {
        return CompletableFuture.supplyAsync(() -> findAllById(ids));
    }

    @Override
    public boolean insert(T entity) {
        if (cascade() && information.hasRelationships()) {
            DefaultGraphCoordinator.of(information).cascadeInsert(entity, nonCascadingView);
            return true;
        }
        return insertDirect(entity);
    }

    private boolean insertDirect(T entity) {
        ID entityId = information.getPrimaryKeyValue(entity);

        Runnable operation = () -> {
            TransactionResult<Boolean> result = repository.insert(entity, transactionContext);
            results.add(result);
            if (!result.getResult().orElse(Boolean.FALSE)) return;
            if (!noCache()) cache.put(entityId, entity);
        };

        if (logOps()) log("INSERT " + entity);

        if (buffered()) pendingOperations.add(operation);
        else            operation.run();

        rollbackCallbacks.add(() -> cache.remove(entityId));
        return true;
    }

    @Override
    public boolean delete(T entity) {
        if (cascade() && information.hasRelationships()) {
            DefaultGraphCoordinator.of(information).cascadeDelete(entity, nonCascadingView);
            return true;
        }
        return deleteDirect(entity);
    }

    private boolean deleteDirect(T entity) {
        ID entityId = information.getPrimaryKeyValue(entity);
        T  previous = findById(entityId);

        Runnable operation = () -> {
            TransactionResult<Boolean> result = repository.delete(entity, transactionContext);
            results.add(result);
            if (result.getResult().orElse(Boolean.FALSE)) return;
            cache.remove(entityId);
        };

        if (logOps()) log("DELETE " + entity);

        if (buffered()) pendingOperations.add(operation);
        else            operation.run();

        rollbackCallbacks.add(() -> { if (previous != null) cache.put(entityId, previous); });
        return true;
    }

    @Override
    public boolean update(T entity) {
        if (cascade() && information.hasRelationships()) {
            DefaultGraphCoordinator.of(information).cascadeUpdate(entity, nonCascadingView);
            return true;
        }
        return updateDirect(entity);
    }

    private boolean updateDirect(T entity) {
        ID entityId = information.getPrimaryKeyValue(entity);
        T  previous = findById(entityId);

        Runnable operation = () -> {
            TransactionResult<Boolean> result = repository.updateAll(entity, transactionContext);
            results.add(result);
            if (result.getResult().orElse(Boolean.FALSE)) return;
            if (!noCache()) cache.put(entityId, entity);
        };

        if (logOps()) log("UPDATE " + entity);

        if (buffered()) pendingOperations.add(operation);
        else            operation.run();

        rollbackCallbacks.add(() -> { if (previous != null) cache.put(entityId, previous); });
        return true;
    }

    /**
     * Inserts all entities in a single transaction context.
     * Cascade, buffering, logging, and rollback are all applied per-entity,
     * exactly as with individual {@link #insert} calls.
     *
     * @param entities the entities to insert
     * @return {@code true} if every insert succeeded
     */
    public boolean insertAll(Iterable<T> entities) {
        boolean ok = true;
        for (T entity : entities) ok &= insert(entity);
        return ok;
    }

    /**
     * Async variant of {@link #insertAll(Iterable)}.
     */
    public CompletableFuture<Boolean> insertAllAsync(Iterable<T> entities) {
        return CompletableFuture.supplyAsync(() -> insertAll(entities));
    }

    /**
     * Updates all entities.
     * Each entity participates in cascading, buffering, logging, and rollback.
     *
     * @param entities the entities to update
     * @return {@code true} if every update succeeded
     */
    public boolean updateAll(Iterable<T> entities) {
        boolean ok = true;
        for (T entity : entities) ok &= update(entity);
        return ok;
    }

    /**
     * Async variant of {@link #updateAll(Iterable)}.
     */
    public CompletableFuture<Boolean> updateAllAsync(Iterable<T> entities) {
        return CompletableFuture.supplyAsync(() -> updateAll(entities));
    }

    /**
     * Deletes all entities.
     * Each entity participates in cascading, buffering, logging, and rollback.
     *
     * @param entities the entities to delete
     * @return {@code true} if every delete succeeded
     */
    public boolean deleteAll(Iterable<T> entities) {
        boolean ok = true;
        for (T entity : entities) ok &= delete(entity);
        return ok;
    }

    /**
     * Async variant of {@link #deleteAll(Iterable)}.
     */
    public CompletableFuture<Boolean> deleteAllAsync(Iterable<T> entities) {
        return CompletableFuture.supplyAsync(() -> deleteAll(entities));
    }

    /**
     * Deletes all entities whose IDs are in the supplied collection.
     * Each deletion participates in the same session semantics as {@link #delete}.
     *
     * @param ids the primary keys to delete
     * @return {@code true} if every deletion succeeded
     */
    public boolean deleteAllById(Iterable<ID> ids) {
        boolean ok = true;
        for (ID key : ids) {
            T entity = findById(key);
            if (entity != null) ok &= delete(entity);
        }
        return ok;
    }

    /**
     * Async variant of {@link #deleteAllById(Iterable)}.
     */
    public CompletableFuture<Boolean> deleteAllByIdAsync(Iterable<ID> ids) {
        return CompletableFuture.supplyAsync(() -> deleteAllById(ids));
    }

    @Override
    public TransactionResult<Boolean> commit() {
        if (buffered()) {
            for (Runnable action : pendingOperations) action.run();
        }

        for (TransactionResult<?> result : results) {
            if (!result.isError()) continue;
            rollback();
            return TransactionResult.failure(
                result.getError().orElse(new IllegalStateException("Operation returned false"))
            );
        }

        try {
            transactionContext.commit();
        } catch (Exception e) {
            rollback();
            return TransactionResult.failure(e);
        }

        pendingOperations.clear();
        rollbackCallbacks.clear();
        results.clear();
        return TransactionResult.success(null);
    }

    @Override
    public void rollback() {
        rollbackCallbacks.forEach(Runnable::run);
        pendingOperations.clear();
        rollbackCallbacks.clear();

        try {
            transactionContext.rollback();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code DefaultSession} does not support batching or auto-flush, so this
     * is always a no-op. Use {@code BUFFERED_WRITE} session option to defer
     * writes instead.
     */
    @Override
    public void setAutoFlush(boolean autoFlush) {}

    /**
     * {@inheritDoc}
     * <p>
     * {@code DefaultSession} does not use a batch size - returns {@code -1}.
     */
    @Override
    public int getBatchSize() { return -1; }

    /**
     * {@inheritDoc}
     * <p>
     * For {@code DefaultSession} this reflects only the buffered
     * {@code pendingOperations} list. When {@code BUFFERED_WRITE} is not set
     * all operations execute immediately and this will always return {@code 0}.
     */
    @Override
    public int getPendingOperationCount() { return pendingOperations.size(); }

    private void log(String message) {
        Logging.info(() -> "[DefaultSession " + id + "] " + message);
    }
}