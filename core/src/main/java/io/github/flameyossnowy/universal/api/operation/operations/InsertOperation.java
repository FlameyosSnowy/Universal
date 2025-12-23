package io.github.flameyossnowy.universal.api.operation.operations;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationMetadata;
import io.github.flameyossnowy.universal.api.operation.OperationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Operation for inserting an entity.
 *
 * @param <T> The entity type
 * @param <C> The connection type
 */
public class InsertOperation<T, C> implements Operation<Boolean, C> {
    private final T entity;
    private final InsertExecutor<T, C> executor;
    private final OperationMetadata metadata;

    public InsertOperation(T entity, InsertExecutor<T, C> executor) {
        this.entity = entity;
        this.executor = executor;
        this.metadata = OperationMetadata.builder()
                .description("Insert entity")
                .cacheable(false)
                .idempotent(false)
                .build();
    }

    @Override
    @NotNull
    public OperationType getOperationType() {
        return OperationType.WRITE;
    }

    @Override
    @NotNull
    public TransactionResult<Boolean> execute(@NotNull OperationContext<C> context) {
        try {
            boolean success = executor.insert(entity, context);
            return TransactionResult.success(success);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    @Nullable
    public OperationMetadata getMetadata() {
        return metadata;
    }

    @FunctionalInterface
    public interface InsertExecutor<T, C> {
        boolean insert(T entity, OperationContext<C> context) throws Exception;
    }
}
