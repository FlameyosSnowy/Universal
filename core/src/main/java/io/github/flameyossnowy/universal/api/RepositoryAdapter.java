package io.github.flameyossnowy.universal.api;

import com.google.errorprone.annotations.CheckReturnValue;
import io.github.flameyossnowy.universal.api.cache.Session;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import java.util.EnumSet;

@SuppressWarnings("unused")
public interface RepositoryAdapter<T, ID, C> extends AutoCloseable {
    /**
     * Creates the repository table, if it does not exist.
     * <p>
     * This method is a convenience wrapper for {@link #createRepository(boolean)} with the argument
     * set to {@code false}.
     */
    @Contract(pure = true)
    
    default TransactionResult<Boolean> createRepository() {
        return this.createRepository(false);
    }

    /**
     * Creates the repository table if it does not exist.
     * <p>
     * If the argument is {@code true}, this method will not do anything if the table already
     * exists. If it is {@code false}, this method will attempt to drop the table before
     * creating it.
     * <p>
     * <b>This method is a one-time operation and will have no effect if the table already
     * exists.</b>
     * @param ifNotExists Whether to create the table if it exists or not.
     */
    @Contract(pure = true)
    
    TransactionResult<Boolean> createRepository(boolean ifNotExists);

    /**
     * Starts a transaction on the underlying storage.
     * <p>
     * This method should be used when performing operations that must be atomic,
     * such as inserting multiple items into the same table.
     * <p>
     * You can use this method to start a transaction, perform your operations,
     * and then call either {@link TransactionContext#commit()} to commit the
     * changes or {@link TransactionContext#rollback()} to roll back the changes.
     * @return A transaction context that will be used to commit or roll back the
     * transaction.
     */
    @Contract(pure = true)
    @CheckReturnValue
    TransactionContext<C> beginTransaction();

    /**
     * Creates a new session.
     * <p>
     * A session is a transaction-scoped object that can be used to
     * perform operations on the underlying storage. It allows you to
     * group multiple operations together into a single transaction,
     * and then commit or roll back the transaction.
     * <p>
     * You can use this method to create a session, perform your operations,
     * and then call either {@link Session#commit()} to commit the
     * changes or {@link Session#close()} to roll back the changes.
     * @return A session that can be used to perform operations on the
     * underlying storage.
     */
    @Contract(pure = true)
    @CheckReturnValue
    Session<ID, T, C> createSession();


    /**
     * Creates a new session.
     * <p>
     * A session is a transaction-scoped object that can be used to
     * perform operations on the underlying storage. It allows you to
     * group multiple operations together into a single transaction,
     * and then commit or roll back the transaction.
     * <p>
     * This method allows you to customize the session with additional
     * options, such as {@link SessionOption#BUFFERED_WRITE} to enable
     * buffered writes, or {@link SessionOption#LOG_OPERATIONS} to log
     * operations performed on the session.
     * <p>
     * You can use this method to create a session, perform your operations,
     * and then call either {@link Session#commit()} to commit the
     * changes or {@link Session#close()} to roll back the changes.
     * @param options A set of options to use when creating the session.
     *                May be empty for default options.
     * @return A session that can be used to perform operations on the
     */
    @Contract(pure = true)
    @CheckReturnValue
    Session<ID, T, C> createSession(EnumSet<SessionOption> options);

    /**
     * Executes a series of operations within a session.
     * <p>
     * This method creates a new session, passes it to the given consumer,
     * and automatically commits the session upon successful execution of
     * the consumer's operations. The session is closed automatically after
     * committing.
     * <p>
     * Usage of this method ensures that resources are properly managed
     * and that the session is always closed, even if an exception occurs.
     *
     * @param sessionConsumer A consumer that performs operations with the
     *                        provided session.
     */
    default TransactionResult<Boolean> withSession(@NotNull Consumer<Session<ID, T, C>> sessionConsumer) {
        try (Session<ID, T, C> session = this.createSession()) {
            sessionConsumer.accept(session);
            return session.commit();
        }
    }

    /**
     * Executes a select query on the underlying storage.
     * <p>
     * This method fetches the results of the given query and returns them as a
     * {@link List<T>}.
     * <p>
     * If the query does not specify any filters, this method will return all
     * items in the repository.
     * @param query The query to execute.
     * @return A {@link List<T>} containing the results of the query.
     */
    @CheckReturnValue
    List<T> find(SelectQuery query);

    /**
     * Executes a select query on the underlying storage with no filters (which essentially returns all items in the database).
     * <p>
     * This method fetches the results of the query and returns them as a
     * {@link List<T>}.
     * @return A {@link List<T>} containing the results of the query.
     */
    @CheckReturnValue
    List<T> find();

    /**
     * Finds and returns an item with the specified primary key from the repository.
     * <p>
     * This method fetches the item that matches the provided key. If no item
     * matches the key, it will return null.
     *
     * @param key The primary key of the item to find.
     * @return The item with the specified key, or null if no such item exists.
     */
    @CheckReturnValue
    T findById(ID key);

    /**
     * Finds and returns the first item that matches the given query.
     * <p>
     * This method fetches the first item that matches the query. If no item
     * matches the query, it will return null.
     * @param query The query to execute. If null, all items will be fetched.
     * @return The first item that matches the query, or null if no such item exists.
     */
    @CheckReturnValue
    T first(SelectQuery query);

    /**
     * Finds and returns the first item in the repository.
     * <p>
     * This method executes a select query with no filters and returns the
     * first item found. If the repository is empty, it will return null.
     *
     * @return The first item in the repository, or null if no items exist.
     */
    @CheckReturnValue
    default T first() {
        return first(null);
    }

    /**
     * Inserts the specified value into the repository within the given transaction context.
     * <p>
     * This method is used to persist a single item into the underlying storage. The transaction
     * context ensures that the operation is atomic and can be committed or rolled back as needed.
     *
     * @param value The item to be inserted into the repository.
     * @param transactionContext The transaction context within which the operation is performed.
     * @return {@code true} if the insertion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> insert(T value, TransactionContext<C> transactionContext);

    /**
     * Inserts the specified values into the repository within the given transaction context.
     * <p>
     * This method is used to persist multiple items into the underlying storage. The transaction
     * context ensures that the operation is atomic and can be committed or rolled back as needed.
     *
     * @param value The items to be inserted into the repository.
     * @param transactionContext The transaction context within which the operation is performed.
     */
    TransactionResult<Boolean> insertAll(List<T> value, TransactionContext<C> transactionContext);

    /**
     * Updates all items in the repository that match the given query within the given transaction context.
     * <p>
     * This method is used to update multiple items in the underlying storage. The transaction
     * context ensures that the operation is atomic and can be committed or rolled back as needed.
     *
     * @param entity The entity to update.
     * @param transactionContext The transaction context within which the operation is performed.
     * @return {@code true} if the update was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> updateAll(T entity, TransactionContext<C> transactionContext);

    /**
     * Deletes items from the repository that match the given query within the provided transaction context.
     * <p>
     * This method is used to remove multiple items from the underlying storage. The transaction
     * context ensures that the operation is atomic and can be committed or rolled back as needed.
     *
     * @param entity The entity to delete.
     * @param transactionContext The transaction context within which the operation is performed.
     * @return {@code true} if the deletion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> delete(T entity, TransactionContext<C> transactionContext);

    /**
     * Deletes the specified value from the repository.
     * <p>
     * This method is used to remove a single item from the underlying storage.
     *
     * @param value The item to be deleted from the repository.
     * @return {@code true} if the deletion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> delete(T value);

    /**
     * Deletes the item with the specified ID from the repository within the provided transaction context.
     * <p>
     * This method is used to remove a single item identified by its ID from the underlying storage.
     * The transaction context ensures that the operation is atomic and can be committed or rolled back as needed.
     *
     * @param entity The ID of the item to be deleted from the repository.
     * @param transactionContext The transaction context within which the operation is performed.
     * @return {@code true} if the deletion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> deleteById(ID entity, TransactionContext<C> transactionContext);

    /**
     * Deletes the item with the specified ID from the repository.
     * <p>
     * This method is used to remove a single item identified by its ID from the underlying storage.
     * @param value The ID of the item to be deleted from the repository.
     * @return {@code true} if the deletion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> deleteById(ID value);

    /**
     * Updates all items in the repository that match the given query within the provided transaction context.
     * <p>
     * This method modifies multiple items in the underlying storage based on the specified criteria
     * in the {@link UpdateQuery}. The transaction context ensures that the update operation is atomic and
     * can be committed or rolled back as needed.
     *
     * @param query The query specifying the items to be updated.
     * @param transactionContext The transaction context within which the operation is performed.
     *
     * @return {@code true} if the update was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<C> transactionContext);

    /**
     * Updates all items in the repository that match the given query.
     * <p>
     * This method modifies multiple items in the underlying storage based on the specified criteria
     * in the {@link UpdateQuery}.
     *
     * @param query The query specifying the items to be updated.
     * @return {@code true} if the update was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query);

    /**
     * Deletes items from the repository that match the given query within the provided transaction context.
     * <p>
     * This method is used to remove multiple items from the underlying storage. The transaction
     * context ensures that the operation is atomic and can be committed or rolled back as needed.
     *
     * @param query The query specifying the items to be deleted.
     * @param tx The transaction context within which the operation is performed.
     * @return {@code true} if the deletion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> delete(DeleteQuery query, TransactionContext<C> tx);

    /**
     * Deletes items from the repository that match the given query.
     * <p>
     * This method is used to remove multiple items from the underlying storage.
     *
     * @param query The query specifying the items to be deleted.
     * @return {@code true} if the deletion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> delete(@NotNull DeleteQuery query);

    /**
     * Inserts the specified value into the repository.
     * <p>
     * This method is used to persist a single item into the underlying storage.
     *
     * @param value The item to be inserted into the repository.
     * @return {@code true} if the insertion was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> insert(T value);

    /**
     * Updates all items in the repository that match the given query.
     * <p>
     * This method is used to update multiple items in the underlying storage.
     *
     * @param entity The entity to update.
     * @return {@code true} if the update was successful, {@code false} otherwise.
     */
    TransactionResult<Boolean> updateAll(T entity);

    /**
     * Inserts the specified list of values into the repository.
     * <p>
     * This method is used to persist multiple items into the underlying storage.
     * <p>
     * The operation is expected to be atomic, meaning all items will be inserted
     * successfully, or none will be, ensuring data consistency.
     *
     * @param query The list of items to be inserted into the repository.
     */
    TransactionResult<Boolean> insertAll(List<T> query);

    /**
     * Removes all items from the repository.
     * <p>
     * This method removes all items from the underlying storage. It is generally
     * not recommended to use this method unless you have a specific reason for
     * doing so, as it is a potentially expensive operation.
     */
    TransactionResult<Boolean> clear();

    /**
     * Creates an index on the repository based on the provided index options.
     * <p>
     * This method is used to define an index on the underlying storage,
     * which can improve query performance by allowing faster data retrieval.
     * The index is created using the specified fields and options defined
     * in the {@link IndexOptions} parameter.
     * <p>
     * If the index options specify a unique index, it will enforce uniqueness
     * across the indexed fields.
     *
     * @param index The options defining the index to be created, including
     *              the fields and type of index.
     * @throws IllegalArgumentException If the index options do not specify any fields.
     */
    TransactionResult<Boolean> createIndex(IndexOptions index);

    /**
     * Creates multiple indexes on the repository based on the provided index options.
     * <p>
     * This method is used to define multiple indexes on the underlying storage,
     * which can improve query performance by allowing faster data retrieval.
     * The indexes are created using the specified fields and options defined
     * in the {@link IndexOptions} parameters.
     * <p>
     * If any of the index options specify a unique index, it will enforce uniqueness
     * across the indexed fields.
     *
     * @return a TransactionResult of {@code true} if the indexes were created successfully, {@code false} otherwise or if no indexes were created.
     * @param indexes The options defining the indexes to be created, including
     *                the fields and type of each index.
     * @throws IllegalArgumentException If any of the index options do not specify any fields.
     */
    default TransactionResult<Boolean> createIndexes(IndexOptions... indexes) {
        if (indexes.length == 0) return TransactionResult.success(false);

        for (IndexOptions elements : indexes) {
            TransactionResult<Boolean> result = this.createIndex(elements);
            if (result.isError()) return result;
        }

        return TransactionResult.success(true);
    }

    /**
     * Creates a dynamic proxy object for the specified interface.
     * <p>
     * This method creates a dynamic proxy object that implements the
     * specified interface. The proxy will delegate any calls it receives
     * to the underlying adapter, allowing you to use the interface as a
     * facade for the underlying repository.
     * <p>
     * The proxy will be created using the provided class loader and will
     * implement the specified interface.
     *
     * @param adapter The interface to be proxied.
     * @return A dynamic proxy object that implements the specified interface.
     */
    <A> A createDynamicProxy(Class<A> adapter);

    /**
     * Retrieves the class type of the identifier used by the repository.
     * <p>
     * This method returns the {@link Class} object representing the type
     * of the primary key or identifier used in the repository.
     *
     * @return The class type of the identifier.
     */
    Class<ID> getIdType();

    /**
     * Executes a select query on the underlying storage asynchronously.
     * <p>
     * This method fetches the results of the given query and returns them as a
     * {@link CompletableFuture} containing a {@link List<T>}.
     * <p>
     * If the query does not specify any filters, this method will return all
     * items in the repository.
     *
     * @param query The query to execute.
     * @return A {@link CompletableFuture} that completes with a {@link List<T>}
     *         containing the results of the query.
     */
    @CheckReturnValue
    default CompletableFuture<List<T>> findAsync(SelectQuery query) {
        return CompletableFuture.supplyAsync(() -> find(query));
    }

    /**
     * Executes a select query on the underlying storage asynchronously.
     * <p>
     * This method is equivalent to calling {@link #findAsync(SelectQuery)} with an empty
     * query, which will return all items in the repository.
     * <p>
     * The query is executed asynchronously and the results are returned as a
     * {@link CompletableFuture} containing a {@link List<T>}.
     *
     * @return A {@link CompletableFuture} that completes with a {@link List<T>}
     *         containing all items in the repository.
     */
    @CheckReturnValue
    default CompletableFuture<List<T>> findAsync() {
        return CompletableFuture.supplyAsync(this::find);
    }

    /**
     * Inserts the specified entity into the repository asynchronously.
     * <p>
     * This method is used to persist a single entity into the underlying storage.
     * If the entity is already present in the repository, this method will do nothing.
     *
     * @param value The entity to insert into the repository.
     * @return a future that completes with {@code true} if the insertion was successful, {@code false} otherwise.
     */
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> insertAsync(T value) {
        return CompletableFuture.supplyAsync(() -> insert(value));
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
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> updateAllAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> updateAll(entity));
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
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> deleteAsync(T entity) {
        return CompletableFuture.supplyAsync(() -> delete(entity));
    }

    /**
     * Asynchronously creates the repository if it does not exist.
     * <p>
     * This method is equivalent to calling {@link #createRepository()} asynchronously.
     * <p>
     * The operation is executed asynchronously and the results are returned as a
     * {@link CompletableFuture} that completes with a {@link Void} result when the
     * operation is complete.
     *
     * @return a future that completes with a {@link Void} result when the operation is complete.
     */
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> createRepositoryAsync() {
        return CompletableFuture.supplyAsync(this::createRepository);
    }

    /**
     * Asynchronously clears the repository.
     * <p>
     * This method is equivalent to calling {@link #clear()} asynchronously.
     * <p>
     * The operation is executed asynchronously and the results are returned as a
     * {@link CompletableFuture} that completes with a {@link Void} result when the
     * operation is complete.
     *
     * @return a future that completes with a {@link Void} result when the operation is complete.
     */
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> clearAsync() {
        return CompletableFuture.supplyAsync(this::clear);
    }

    /**
     * Asynchronously creates an index on the repository based on the provided index options.
     * <p>
     * This method is used to define an index on the underlying storage,
     * which can enhance query performance by enabling faster data retrieval.
     * The index is created using the specified fields and options defined
     * in the {@link IndexOptions} parameter.
     * <p>
     * If the index options specify a unique index, it will enforce uniqueness
     * across the indexed fields.
     *
     * @param index The options defining the index to be created, including
     *              the fields and type of index.
     * @return a future that completes with a {@link Void} result when the
     *         operation is complete.
     * @throws IllegalArgumentException If the index options do not specify any fields.
     */
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> createIndexAsync(IndexOptions index) {
        return CompletableFuture.supplyAsync(() -> createIndex(index));
    }

    /**
     * Asynchronously creates multiple indexes on the repository based on the provided index options.
     * <p>
     * This method is used to define multiple indexes on the underlying storage,
     * which can enhance query performance by enabling faster data retrieval.
     * The indexes are created using the specified fields and options defined
     * in the {@link IndexOptions} parameters.
     * <p>
     * If any of the index options specify a unique index, it will enforce uniqueness
     * across the indexed fields.
     *
     * @param indexes The options defining the indexes to be created, including
     *                the fields and type of each index.
     * @return a future that completes with a {@link Void} result when the
     *         operation is complete.
     * @throws IllegalArgumentException If any of the index options do not specify any fields.
     */
    @CheckReturnValue
    default CompletableFuture<TransactionResult<Boolean>> createIndexesAsync(IndexOptions... indexes) {
        return CompletableFuture.supplyAsync(() -> createIndexes(indexes));
    }

    /**
     * Retrieves the repository information associated with this adapter.
     * <p>
     * This method is intended for internal use and provides metadata
     * about the repository, such as the entity class, table name, fields,
     * constraints, and other configuration details.
     *
     * @return the {@link RepositoryInformation} instance containing
     *         metadata about the repository.
     */
    @ApiStatus.Internal
    RepositoryInformation getInformation();

    Class<T> getElementType();
}
