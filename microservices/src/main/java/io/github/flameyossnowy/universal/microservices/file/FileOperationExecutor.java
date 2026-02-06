package io.github.flameyossnowy.universal.microservices.file;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Operation executor for file-based repositories.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class FileOperationExecutor<T, ID> implements OperationExecutor<T, ID, FileContext> {
    private final FileRepositoryAdapter<T, ID> adapter;

    public FileOperationExecutor(FileRepositoryAdapter<T, ID> adapter) {
        this.adapter = adapter;
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRead(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeWrite(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeUpdate(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeDelete(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeSchema(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeCustom(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRemote(
            @NotNull Operation<T, ID, R, FileContext> operation,
            @NotNull OperationContext<T, ID, FileContext> context) {
        throw new UnsupportedOperationException("Remote operations not supported for file repositories");
    }
}
