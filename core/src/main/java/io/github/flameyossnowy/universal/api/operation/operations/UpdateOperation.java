package io.github.flameyossnowy.universal.api.operation.operations;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationMetadata;
import io.github.flameyossnowy.universal.api.operation.OperationType;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Operation for updating entities.
 *
 * @param <T> The entity type
 * @param <C> The connection type
 */
public class UpdateOperation<T, ID, C> implements Operation<T, ID, Boolean, C> {
    private final T entity;
    private final UpdateQuery query;
    private final UpdateExecutor<T, ID, C> executor;
    private final OperationMetadata metadata;

    public UpdateOperation(T entity, UpdateExecutor<T, ID, C> executor) {
        this.entity = entity;
        this.query = null;
        this.executor = executor;
        this.metadata = OperationMetadata.builder()
                .description("Update entity")
                .cacheable(false)
                .idempotent(true)
                .build();
    }

    public UpdateOperation(UpdateQuery query, UpdateExecutor<T, ID, C> executor) {
        this.entity = null;
        this.query = query;
        this.executor = executor;
        this.metadata = OperationMetadata.builder()
                .description("Update entities matching query")
                .cacheable(false)
                .idempotent(true)
                .build();
    }

    @Override
    @NotNull
    public OperationType getOperationType() {
        return OperationType.UPDATE;
    }

    @Override
    @NotNull
    public TransactionResult<Boolean> execute(@NotNull OperationContext<T, ID, C> context) {
        try {
            boolean success = entity != null 
                    ? executor.updateEntity(entity, context)
                    : executor.updateByQuery(query, context);
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

    public interface UpdateExecutor<T, ID, C> {
        boolean updateEntity(T entity, OperationContext<T, ID, C> context) throws Exception;
        boolean updateByQuery(UpdateQuery query, OperationContext<T, ID, C> context) throws Exception;
    }
}
