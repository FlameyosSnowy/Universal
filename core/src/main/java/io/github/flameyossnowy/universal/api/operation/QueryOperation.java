package io.github.flameyossnowy.universal.api.operation;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.options.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An operation that wraps a Query for execution.
 *
 * @param <R> The result type
 * @param <C> The connection type
 */
public abstract class QueryOperation<T, ID, R, C> implements Operation<T, ID, R, C> {
    protected final Query query;
    protected final OperationMetadata metadata;

    protected QueryOperation(@Nullable Query query, @Nullable OperationMetadata metadata) {
        this.query = query;
        this.metadata = metadata;
    }

    @Nullable
    public Query getQuery() {
        return query;
    }

    @Override
    @Nullable
    public OperationMetadata getMetadata() {
        return metadata;
    }

    @Override
    @NotNull
    public abstract TransactionResult<R> execute(@NotNull OperationContext<T, ID, C> context);
}
