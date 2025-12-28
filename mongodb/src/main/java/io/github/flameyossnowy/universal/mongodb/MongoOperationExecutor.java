package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.ClientSession;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Operation executor for MongoDB repositories.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class MongoOperationExecutor<T, ID> implements OperationExecutor<ClientSession> {
    private final MongoRepositoryAdapter<T, ID> adapter;

    public MongoOperationExecutor(MongoRepositoryAdapter<T, ID> adapter) {
        this.adapter = adapter;
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRead(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeWrite(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeUpdate(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeDelete(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeSchema(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeCustom(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRemote(
            @NotNull Operation<R, ClientSession> operation,
            @NotNull OperationContext<ClientSession> context) {
        throw new UnsupportedOperationException("Remote operations not supported for MongoDB repositories");
    }
}
