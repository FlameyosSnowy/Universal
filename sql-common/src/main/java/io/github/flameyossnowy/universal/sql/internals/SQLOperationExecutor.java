package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;

/**
 * Operation executor for SQL-based repositories.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class SQLOperationExecutor<T, ID> implements OperationExecutor<Connection> {
    private final AbstractRelationalRepositoryAdapter<T, ID> adapter;

    public SQLOperationExecutor(AbstractRelationalRepositoryAdapter<T, ID> adapter) {
        this.adapter = adapter;
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRead(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeWrite(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeUpdate(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeDelete(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeSchema(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeCustom(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRemote(
            @NotNull Operation<R, Connection> operation,
            @NotNull OperationContext<Connection> context) {
        throw new UnsupportedOperationException("Remote operations not supported for SQL repositories");
    }
}
