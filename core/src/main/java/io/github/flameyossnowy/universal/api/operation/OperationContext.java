package io.github.flameyossnowy.universal.api.operation;

import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Context object that contains all information needed to execute an operation.
 *
 * @param <C> The connection/context type
 */
public record OperationContext<T, ID, C>(
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull TypeResolverRegistry resolverRegistry,
        @Nullable TransactionContext<C> transactionContext,
        @NotNull Map<String, Object> attributes,
        @NotNull OperationExecutor<T, ID, C> executor
) {
    public OperationContext(
            @NotNull RepositoryModel<T, ID> repositoryModel,
            @NotNull TypeResolverRegistry resolverRegistry,
            @NotNull OperationExecutor<T, ID, C> executor
    ) {
        this(repositoryModel, resolverRegistry, null, new HashMap<>(), executor);
    }

    @NotNull
    public Optional<TransactionContext<C>> getTransactionContext() {
        return Optional.ofNullable(transactionContext);
    }

    /**
     * Creates a new context with the given transaction context.
     */
    @NotNull
    public OperationContext<T, ID, C> withTransaction(@NotNull TransactionContext<C> transactionContext) {
        return new OperationContext<>(
            repositoryModel,
                resolverRegistry,
                transactionContext,
                attributes,
                executor
        );
    }

    /**
     * Sets an attribute in this context.
     */
    public void setAttribute(@NotNull String key, @Nullable Object value) {
        attributes.put(key, value);
    }

    /**
     * Gets an attribute from this context.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(@NotNull String key) {
        return Optional.ofNullable((T) attributes.get(key));
    }

    /**
     * Gets an attribute or throws if not present.
     */
    @SuppressWarnings("unchecked")
    public <T> T getRequiredAttribute(@NotNull String key) {
        Object value = attributes.get(key);
        if (value == null) {
            throw new IllegalStateException("Required attribute not found: " + key);
        }
        return (T) value;
    }
}
