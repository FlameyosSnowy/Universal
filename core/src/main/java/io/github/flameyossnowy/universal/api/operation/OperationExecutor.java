package io.github.flameyossnowy.universal.api.operation;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for executing operations on a specific repository backend.
 * <p>
 * This provides the low-level execution capabilities that different repository
 * adapters will implement.
 *
 * @param <C> The connection/context type
 */
public interface OperationExecutor<T, ID, C> {
    /**
     * Executes a read operation.
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeRead(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);

    /**
     * Executes a write operation.
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeWrite(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);

    /**
     * Executes an update operation.
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeUpdate(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);

    /**
     * Executes a delete operation.
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeDelete(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);

    /**
     * Executes a schema operation.
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeSchema(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);

    /**
     * Executes a custom operation.
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeCustom(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);

    /**
     * Executes a remote operation (for microservices).
     *
     * @param operation The operation to execute
     * @param context The operation context
     * @param <R> The result type
     * @return The result of the operation
     */
    @NotNull
    <R> TransactionResult<R> executeRemote(
            @NotNull Operation<T, ID, R, C> operation,
            @NotNull OperationContext<T, ID, C> context);
}
