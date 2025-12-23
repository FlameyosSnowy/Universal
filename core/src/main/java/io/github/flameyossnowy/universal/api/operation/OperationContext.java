package io.github.flameyossnowy.universal.api.operation;

import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
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
public record OperationContext<C>(
        @NotNull RepositoryInformation repositoryInformation,
        @NotNull TypeResolverRegistry resolverRegistry,
        @Nullable TransactionContext<C> transactionContext,
        @NotNull Map<String, Object> attributes,
        @NotNull OperationExecutor<C> executor
) {
    public OperationContext(
            @NotNull RepositoryInformation repositoryInformation,
            @NotNull TypeResolverRegistry resolverRegistry,
            @NotNull OperationExecutor<C> executor
    ) {
        this(repositoryInformation, resolverRegistry, null, new HashMap<>(), executor);
    }

    @NotNull
    public Optional<TransactionContext<C>> getTransactionContext() {
        return Optional.ofNullable(transactionContext);
    }

    /**
     * Creates a new context with the given transaction context.
     */
    @NotNull
    public OperationContext<C> withTransaction(@NotNull TransactionContext<C> transactionContext) {
        return new OperationContext<>(
                repositoryInformation,
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
