package io.github.flameyossnowy.universal.api.cache;

import com.google.errorprone.annotations.CheckReturnValue;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public interface DatabaseSession<ID, T, C> extends TransactionContext<C>, AutoCloseable {
    /**
     * Retrieves the cache associated with this session.
     *
     * @return the cache associated with this session
     */
    SessionCache<ID, T> getCache();

    /**
     * Gets the unique identifier for this session.
     *
     * @return the unique identifier for this session
     */
    long getId();

    /**
     * Enables or disables automatic flushing of pending operations when an
     * internal batch size threshold is reached.
     * <p>
     * Not all implementations support batching; the default is a no-op.
     *
     * @param autoFlush {@code true} to enable auto-flush, {@code false} to disable
     */
    default void setAutoFlush(boolean autoFlush) {}

    /**
     * Returns the batch size used by this session for auto-flush decisions.
     * <p>
     * Returns {@code -1} for implementations that do not support batching
     * (e.g. {@link io.github.flameyossnowy.universal.api.cache.DefaultSession}).
     *
     * @return the batch size, or {@code -1} if not applicable
     */
    default int getBatchSize() { return -1; }

    /**
     * Returns the number of operations that are currently staged but not yet
     * committed or flushed to the underlying storage.
     * <p>
     * For {@link io.github.flameyossnowy.universal.api.cache.DefaultSession} this
     * reflects only the {@code pendingOperations} list (buffered-write mode).
     * For batch-aware sessions ({@code FileSession}, {@code NetworkSession}) it
     * also includes the insert / update / delete staging maps.
     *
     * @return the number of pending operations
     */
    default int getPendingOperationCount() { return 0; }

    /**
     * Rollbacks the current transaction, discarding all modifications made
     * since the start of the transaction.
     */
    void rollback();

    /**
     * Commits all changes in the current session.
     *
     * @return a result object indicating success or failure
     */
    @CheckReturnValue
    TransactionResult<Boolean> commit();

    /**
     * Commits all changes in the current session asynchronously.
     *
     * @return a future that resolves to the commit result
     */
    default CompletableFuture<TransactionResult<Boolean>> commitAsync() {
        return CompletableFuture.supplyAsync(this::commit);
    }

    /**
     * Closes the current session, releasing any resources associated with it.
     */
    void close();

    /**
     * Returns the underlying connection for this session.
     */
    C connection();

    /**
     * Finds and returns the entity with the specified primary key.
     * The result may be served from cache depending on session options.
     *
     * @param key the primary key to look up
     * @return the entity, or {@code null} if not found
     */
    T findById(ID key);

    /**
     * Async variant of {@link #findById(Object)}.
     */
    default CompletableFuture<T> findByIdAsync(ID key) {
        return CompletableFuture.supplyAsync(() -> findById(key));
    }

    /**
     * Finds multiple entities by their primary keys.
     * <p>
     * Implementations should minimise round-trips: cache hits are returned
     * immediately and remaining IDs are fetched from storage in one batch call.
     * IDs that cannot be found are absent from the returned map.
     *
     * @param ids the primary keys to look up
     * @return a map of ID -> entity for every ID that was found
     */
    default Map<ID, T> findAllById(Collection<ID> ids) {
        Map<ID, T> result = new java.util.LinkedHashMap<>(ids.size());
        for (ID key : ids) {
            T entity = findById(key);
            if (entity != null) result.put(key, entity);
        }
        return result;
    }

    /**
     * Async variant of {@link #findAllById(Collection)}.
     */
    default CompletableFuture<Map<ID, T>> findAllByIdAsync(Collection<ID> ids) {
        return CompletableFuture.supplyAsync(() -> findAllById(ids));
    }

    /**
     * Inserts the specified entity into the repository.
     *
     * @param entity the entity to insert
     * @return {@code true} if the insertion was successful
     */
    boolean insert(T entity);

    /**
     * Async variant of {@link #insert(Object)}.
     */
    default CompletableFuture<Boolean> insertAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> insert(entity));
    }

    /**
     * Updates the specified entity in the repository.
     *
     * @param entity the entity to update
     * @return {@code true} if the update was successful
     */
    boolean update(T entity);

    /**
     * Async variant of {@link #update(Object)}.
     */
    default CompletableFuture<Boolean> updateAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> update(entity));
    }

    /**
     * Deletes the specified entity from the repository.
     *
     * @param entity the entity to delete
     * @return {@code true} if the deletion was successful
     */
    boolean delete(T entity);

    /**
     * Async variant of {@link #delete(Object)}.
     */
    default CompletableFuture<Boolean> deleteAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> delete(entity));
    }

    /**
     * Inserts all entities in the supplied iterable.
     * <p>
     * The default implementation delegates to {@link #insert(Object)} for each
     * entity so that cascade, buffering, logging and rollback semantics are
     * preserved. Implementations may override this with a more efficient
     * batched path.
     *
     * @param entities the entities to insert
     * @return {@code true} if every insertion was successful
     */
    default boolean insertAll(Iterable<T> entities) {
        boolean ok = true;
        for (T entity : entities) ok &= insert(entity);
        return ok;
    }

    /**
     * Async variant of {@link #insertAll(Iterable)}.
     */
    default CompletableFuture<Boolean> insertAllAsync(Iterable<T> entities) {
        return CompletableFuture.supplyAsync(() -> insertAll(entities));
    }

    /**
     * Updates all entities in the supplied iterable.
     * <p>
     * The default implementation delegates to {@link #update(Object)} for each
     * entity so that all session semantics are preserved.
     *
     * @param entities the entities to update
     * @return {@code true} if every update was successful
     */
    default boolean updateAll(Iterable<T> entities) {
        boolean ok = true;
        for (T entity : entities) ok &= update(entity);
        return ok;
    }

    /**
     * Async variant of {@link #updateAll(Iterable)}.
     */
    default CompletableFuture<Boolean> updateAllAsync(Iterable<T> entities) {
        return CompletableFuture.supplyAsync(() -> updateAll(entities));
    }

    /**
     * Deletes all entities in the supplied iterable.
     * <p>
     * The default implementation delegates to {@link #delete(Object)} for each
     * entity so that all session semantics are preserved.
     *
     * @param entities the entities to delete
     * @return {@code true} if every deletion was successful
     */
    default boolean deleteAll(Iterable<T> entities) {
        boolean ok = true;
        for (T entity : entities) ok &= delete(entity);
        return ok;
    }

    /**
     * Async variant of {@link #deleteAll(Iterable)}.
     */
    default CompletableFuture<Boolean> deleteAllAsync(Iterable<T> entities) {
        return CompletableFuture.supplyAsync(() -> deleteAll(entities));
    }

    /**
     * Deletes all entities whose primary keys are in the supplied collection.
     * <p>
     * Each ID is resolved via {@link #findById(Object)} before deletion so that
     * cascade and rollback semantics work correctly. IDs that resolve to
     * {@code null} are silently skipped.
     *
     * @param ids the primary keys to delete
     * @return {@code true} if every deletion was successful
     */
    default boolean deleteAllById(Iterable<ID> ids) {
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
    default CompletableFuture<Boolean> deleteAllByIdAsync(Iterable<ID> ids) {
        return CompletableFuture.supplyAsync(() -> deleteAllById(ids));
    }

    @CheckReturnValue
    default TransactionResult<Boolean> runVoid(Consumer<DatabaseSession<ID, T, C>> block) {
        return run(session -> {
            block.accept(session);
            return true;
        });
    }

    @CheckReturnValue
    default <R> TransactionResult<R> run(Function<DatabaseSession<ID, T, C>, R> block) {
        try {
            R result = block.apply(this);
            TransactionResult<Boolean> commitResult = commit();
            if (commitResult.isError()) {
                rollback();
                return TransactionResult.failure(commitResult.error());
            }
            return TransactionResult.success(result);
        } catch (Throwable t) {
            rollback();
            return TransactionResult.failure(t);
        }
    }

    default <R> CompletableFuture<TransactionResult<R>> runAsync(
        Function<DatabaseSession<ID, T, C>, R> block
    ) {
        return CompletableFuture.supplyAsync(() -> run(block));
    }
}