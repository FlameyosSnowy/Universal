package io.github.flameyossnowy.universal.microservices.network;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;

/**
 * Operation executor for network-based repositories.
 *
 * @param <T>  The entity type
 * @param <ID> The ID type
 */
public record NetworkOperationExecutor<T, ID>(
        NetworkRepositoryAdapter<T, ID> adapter) implements OperationExecutor<HttpClient> {

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRead(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeWrite(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeUpdate(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeDelete(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeSchema(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        throw new UnsupportedOperationException("Schema operations not supported for network repositories");
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeCustom(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        return operation.execute(context);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> executeRemote(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull OperationContext<HttpClient> context) {
        return operation.execute(context);
    }
}
