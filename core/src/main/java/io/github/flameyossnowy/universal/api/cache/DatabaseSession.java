package io.github.flameyossnowy.universal.api.cache;

import com.google.errorprone.annotations.CheckReturnValue;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public interface DatabaseSession<ID, T, C> extends TransactionContext<C>, AutoCloseable {
    /**
     * Retrieves the cache associated with this session.
     * @return the cache associated with this session.
     */
    SessionCache<ID, T> getCache();

    /**
     * Gets the unique identifier for this session.
     * This identifier is used to distinguish between different sessions, and
     * is unique for each session instance.
     * @return the unique identifier for this session
     */
    long getId();

    /**
     * Rollbacks the current transaction, discarding all modifications made
     * since the start of the transaction.
     * <p>
     * This method may throw exceptions if a transaction is not currently
     * active (i.e., if {@link #commit()} has already been called).
     */
    void rollback();

    /**
     * Inserts the specified entity into the repository.
     * This method is used to persist a single entity into the underlying storage.
     * If the entity is already present in the repository, this method will do nothing.
     *
     * @param entity The entity to insert into the repository.
     * @return {@code true} if the insertion was successful, {@code false} otherwise.
     */
    boolean insert(T entity);

    /**
     * Deletes the specified entity from the repository.
     * <p>
     * This method is used to remove a single entity from the underlying storage.
     * It will perform the necessary operations to ensure the entity is
     * no longer present in the repository. If the entity does not exist,
     * this method will do nothing.
     *
     * @param entity The entity to be deleted from the repository.
     * @return {@code true} if the entity was successfully deleted, {@code false} otherwise.
     */
    boolean delete(T entity);

    /**
     * Updates the specified entity in the repository.
     * <p>
     * This method is used to modify an existing entity in the underlying storage.
     * The entity must already exist in the repository for the update to be successful.
     *
     * @param entity The entity to update in the repository.
     * @return {@code true} if the update was successful, {@code false} otherwise.
     */
    boolean update(T entity);

    /**
     * Inserts the specified entity into the repository asynchronously.
     * This method is used to persist a single entity into the underlying storage.
     * If the entity is already present in the repository, this method will do nothing.
     *
     * @param entity The entity to insert into the repository.
     * @return a future that completes with {@code true} if the insertion was successful, {@code false} otherwise.
     */
    default CompletableFuture<Boolean> insertAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> insert(entity));
    }

    /**
     * Deletes the specified entity from the repository asynchronously.
     * <p>
     * This method is used to remove a single entity from the underlying storage.
     * It will perform the necessary operations to ensure the entity is
     * no longer present in the repository. If the entity does not exist,
     * this method will do nothing.
     *
     * @param entity The entity to be deleted from the repository.
     * @return a future that completes with {@code true} if the entity was successfully deleted, {@code false} otherwise.
     */
    default CompletableFuture<Boolean> deleteAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> delete(entity));
    }

    /**
     * Updates the specified entity in the repository asynchronously.
     * <p>
     * This method is used to modify an existing entity in the underlying storage.
     * The entity must already exist in the repository for the update to be successful.
     *
     * @param entity The entity to update in the repository.
     * @return a future that completes with {@code true} if the update was successful, {@code false} otherwise.
     */
    default CompletableFuture<Boolean> updateAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> update(entity));
    }

    /**
     * Finds and returns an item with the specified primary key from the repository asynchronously.
     * <p>
     * This method fetches the item that matches the provided key. If no item
     * matches the key, it will return null.
     *
     * @param key The primary key of the item to find.
     * @return a future that completes with the item with the specified key, or null if no such item exists.
     */
    default CompletableFuture<T> findByIdAsync(ID key) {
        return CompletableFuture.supplyAsync(() -> findById(key));
    }

    /**
     * Closes the current session, releasing any resources associated with it.
     * <p>
     * This method is typically called to clean up resources when the session
     * is no longer needed. Implementations should ensure that any open
     * transactions are properly rolled back if not committed before closing.
     * <p>
     * After this method is called, the session should not be used for any
     * further operations.
     */
    void close();

    /**
     * Finds and returns an item with the specified primary key from the repository.
     * <p>
     * This method fetches the item that matches the provided key. If no item
     * matches the key, it will return null.
     *
     * @param key The primary key of the item to find.
     * @return The item with the specified key, or null if no such item exists, may be cached.
     */
    T findById(ID key);

    /**
     * Commits all changes in the current session.
     * <p>
     * This method should be called when all operations in the session are complete
     * and the results should be persisted. If the commit is successful, all
     * changes will be persisted.
     *
     * @return a result object that can be used to find out if the commit was
     * successful or not.
     */
    @CheckReturnValue
    TransactionResult<Boolean> commit();

    /**
     * Commits all changes in the current session asynchronously.
     * <p>
     * This method should be called when all operations in the session are complete
     * and the results should be persisted. If the commit is successful, all
     * changes will be persisted.
     *
     * @return a result object that can be used to find out if the commit was
     * successful or not.
     */
    default CompletableFuture<TransactionResult<Boolean>> commitAsync() {
        return CompletableFuture.supplyAsync(this::commit);
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
