package io.github.flameyossnowy.universal.api;

import com.google.errorprone.annotations.CheckReturnValue;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Base repository adapter that works with Operation abstraction.
 * <p>
 * This adapter provides a generic way to execute operations on any storage backend,
 * making it easy to extend from database ORMs to microservices.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 * @param <C> The connection/context type
 */
@SuppressWarnings("unused")
public interface BaseRepositoryAdapter<T, ID, C> extends AutoCloseable {
    /**
     * Executes an operation synchronously.
     *
     * @param operation The operation to execute
     * @param <R> The result type
     * @return The result of the operation
     */
    @CheckReturnValue
    @NotNull
    <R> TransactionResult<R> execute(@NotNull Operation<R, C> operation);

    /**
     * Executes an operation asynchronously.
     *
     * @param operation The operation to execute
     * @param <R> The result type
     * @return A CompletableFuture containing the result
     */
    @CheckReturnValue
    @NotNull
    default <R> CompletableFuture<TransactionResult<R>> executeAsync(@NotNull Operation<R, C> operation) {
        return CompletableFuture.supplyAsync(() -> execute(operation));
    }

    /**
     * Executes an operation within a transaction context.
     *
     * @param operation The operation to execute
     * @param transactionContext The transaction context
     * @param <R> The result type
     * @return The result of the operation
     */
    @CheckReturnValue
    @NotNull
    <R> TransactionResult<R> execute(
            @NotNull Operation<R, C> operation,
            @NotNull TransactionContext<C> transactionContext);

    /**
     * Gets the operation context for this adapter.
     *
     * @return The operation context
     */
    @NotNull
    OperationContext<C> getOperationContext();

    /**
     * Gets the operation executor for this adapter.
     *
     * @return The operation executor
     */
    @NotNull
    OperationExecutor<C> getOperationExecutor();

    /**
     * Gets the repository information.
     *
     * @return The repository information
     */
    @NotNull
    RepositoryInformation getRepositoryInformation();

    /**
     * Gets the type resolver registry.
     *
     * @return The type resolver registry
     */
    @NotNull
    TypeResolverRegistry getTypeResolverRegistry();

    /**
     * Gets the entity type.
     *
     * @return The entity class
     */
    @NotNull
    Class<T> getEntityType();

    /**
     * Gets the ID type.
     *
     * @return The ID class
     */
    @NotNull
    Class<ID> getIdType();

    /**
     * Starts a transaction on the underlying storage.
     *
     * @return A transaction context
     */
    @CheckReturnValue
    @NotNull
    TransactionContext<C> beginTransaction();

    /**
     * Default implementation that delegates to the operation executor.
     */
    @NotNull
    default <R> TransactionResult<R> executeOperation(@NotNull Operation<R, C> operation) {
        OperationContext<C> context = getOperationContext();
        OperationExecutor<C> executor = getOperationExecutor();

        return switch (operation.getOperationType()) {
            case READ -> executor.executeRead(operation, context);
            case WRITE -> executor.executeWrite(operation, context);
            case UPDATE -> executor.executeUpdate(operation, context);
            case DELETE -> executor.executeDelete(operation, context);
            case SCHEMA -> executor.executeSchema(operation, context);
            case CUSTOM -> executor.executeCustom(operation, context);
            case REMOTE -> executor.executeRemote(operation, context);
            default -> operation.execute(context);
        };
    }
}
