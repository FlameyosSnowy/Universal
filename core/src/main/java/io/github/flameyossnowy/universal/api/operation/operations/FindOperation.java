package io.github.flameyossnowy.universal.api.operation.operations;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationMetadata;
import io.github.flameyossnowy.universal.api.operation.OperationType;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Operation for finding entities.
 *
 * @param <T> The entity type
 * @param <C> The connection type
 */
public class FindOperation<T, C> implements Operation<List<T>, C> {
    private final SelectQuery query;
    private final FindExecutor<T, C> executor;
    private final OperationMetadata metadata;

    public FindOperation(SelectQuery query, FindExecutor<T, C> executor) {
        this.query = query;
        this.executor = executor;
        this.metadata = OperationMetadata.builder()
                .description("Find entities matching query")
                .cacheable(true)
                .idempotent(true)
                .build();
    }

    @Override
    @NotNull
    public OperationType getOperationType() {
        return OperationType.READ;
    }

    @Override
    @NotNull
    public TransactionResult<List<T>> execute(@NotNull OperationContext<C> context) {
        try {
            List<T> results = executor.find(query, context);
            return TransactionResult.success(results);
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
    public interface FindExecutor<T, C> {
        List<T> find(SelectQuery query, OperationContext<C> context) throws Exception;
    }
}
