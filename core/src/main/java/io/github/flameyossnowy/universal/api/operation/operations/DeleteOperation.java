package io.github.flameyossnowy.universal.api.operation.operations;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationMetadata;
import io.github.flameyossnowy.universal.api.operation.OperationType;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Operation for deleting entities.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 * @param <C> The connection type
 */
public class DeleteOperation<T, ID, C> implements Operation<Boolean, C> {
    private final T entity;
    private final ID id;
    private final DeleteQuery query;
    private final DeleteExecutor<T, ID, C> executor;
    private final OperationMetadata metadata;

    DeleteOperation(T entity, ID id, DeleteQuery query, DeleteExecutor<T, ID, C> executor) {
        this.entity = entity;
        this.id = id;
        this.query = query;
        this.executor = executor;
        this.metadata = OperationMetadata.builder()
                .description("Delete entity")
                .cacheable(false)
                .idempotent(true)
                .build();
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID, C> DeleteOperation<T, ID, C> fromEntity(T entity, DeleteExecutor<T, ID, C> executor) {
        return new DeleteOperation<>(entity, null, null, executor);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID, C> DeleteOperation<T, ID, C> fromId(ID id, DeleteExecutor<T, ID, C> executor) {
        return new DeleteOperation<>(null, id, null, executor);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID, C> DeleteOperation<T, ID, C> fromQuery(DeleteQuery query, DeleteExecutor<T, ID, C> executor) {
        return new DeleteOperation<>(null, null, query, executor);
    }

    @Override
    @NotNull
    public OperationType getOperationType() {
        return OperationType.DELETE;
    }

    @Override
    @NotNull
    public TransactionResult<Boolean> execute(@NotNull OperationContext<C> context) {
        try {
            boolean success;
            if (entity != null) {
                success = executor.deleteEntity(entity, context);
            } else if (id != null) {
                success = executor.deleteById(id, context);
            } else {
                success = executor.deleteByQuery(query, context);
            }
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

    public interface DeleteExecutor<T, ID, C> {
        boolean deleteEntity(T entity, OperationContext<C> context) throws Exception;
        boolean deleteById(ID id, OperationContext<C> context) throws Exception;
        boolean deleteByQuery(DeleteQuery query, OperationContext<C> context) throws Exception;
    }
}
