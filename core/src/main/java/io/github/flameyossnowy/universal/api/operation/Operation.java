package io.github.flameyossnowy.universal.api.operation;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an executable operation that can be performed on a repository.
 * <p>
 * Operations are the core abstraction for all repository interactions, allowing
 * different types of repositories (databases, files, microservices) to work with
 * a common interface.
 * <p>
 * Operations can be executed synchronously or asynchronously, with or without
 * transaction context.
 *
 * @param <R> The result type of the operation
 * @param <C> The connection/context type used by the underlying storage
 */
public interface Operation<T, ID, R, C> {
    /**
     * Gets the type of this operation.
     *
     * @return the operation type
     */
    @NotNull
    OperationType getOperationType();

    /**
     * Executes this operation synchronously.
     *
     * @param context The execution context containing necessary information
     * @return The result of the operation
     */
    @NotNull
    TransactionResult<R> execute(@NotNull OperationContext<T, ID, C> context);

    /**
     * Executes this operation asynchronously.
     *
     * @param context The execution context containing necessary information
     * @return A CompletableFuture that will complete with the operation result
     */
    @NotNull
    default CompletableFuture<TransactionResult<R>> executeAsync(@NotNull OperationContext<T, ID, C> context) {
        return CompletableFuture.supplyAsync(() -> execute(context));
    }

    /**
     * Executes this operation within a transaction context.
     *
     * @param context The execution context
     * @param transactionContext The transaction context
     * @return The result of the operation
     */
    @NotNull
    default TransactionResult<R> executeWithTransaction(
            @NotNull OperationContext<T, ID, C> context,
            @NotNull TransactionContext<C> transactionContext) {
        return execute(context.withTransaction(transactionContext));
    }

    /**
     * Returns metadata about this operation.
     *
     * @return operation metadata
     */
    @Nullable
    default OperationMetadata getMetadata() {
        return null;
    }

    /**
     * Validates this operation before execution.
     *
     * @return true if the operation is valid, false otherwise
     */
    default boolean validate() {
        return true;
    }
}
