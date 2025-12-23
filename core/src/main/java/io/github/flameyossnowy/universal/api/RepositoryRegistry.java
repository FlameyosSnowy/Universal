package io.github.flameyossnowy.universal.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for managing repository adapters across different platforms.
 * <p>
 * This registry enables cross-platform repository linking by maintaining
 * a centralized map of named adapters. Adapters can be registered with
 * unique names and retrieved for relationship resolution.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Register adapters
 * MySQLRepositoryAdapter<User, UUID> userAdapter = ...;
 * CassandraRepositoryAdapter<PathEntry, Path> cacheAdapter = ...;
 *
 * RepositoryRegistry.register("user-adapter", userAdapter);
 * RepositoryRegistry.register("cache-adapter", cacheAdapter);
 *
 * // Adapters can now reference each other via @ExternalRepository
 * }</pre>
 *
 * @author FlameyosFlow
 */
public final class RepositoryRegistry {
    private static final Map<String, RepositoryAdapter<?, ?, ?>> ADAPTERS = new ConcurrentHashMap<>(16);
    private static final Map<Class<?>, String> TYPE_TO_ADAPTER = new ConcurrentHashMap<>(16);

    private RepositoryRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a repository adapter with a unique name.
     *
     * @param name the unique name for this adapter
     * @param adapter the repository adapter to register
     * @param <T> the entity type
     * @param <ID> the ID type
     * @param <C> the connection type
     * @throws IllegalArgumentException if an adapter with this name already exists
     */
    public static <T, ID, C> void register(@NotNull String name, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        ADAPTERS.put(name, adapter);
        TYPE_TO_ADAPTER.put(adapter.getElementType(), name);
    }

    /**
     * Registers a repository adapter, allowing replacement of existing adapters.
     *
     * @param name the unique name for this adapter
     * @param adapter the repository adapter to register
     * @param <T> the entity type
     * @param <ID> the ID type
     * @param <C> the connection type
     */
    public static <T, ID, C> void registerOrReplace(@NotNull String name, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        ADAPTERS.put(name, adapter);
        TYPE_TO_ADAPTER.put(adapter.getElementType(), name);
    }

    /**
     * Retrieves a repository adapter by name.
     *
     * @param name the adapter name
     * @param <T> the entity type
     * @param <ID> the ID type
     * @param <C> the connection type
     * @return the adapter, or null if not found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T, ID, C> RepositoryAdapter<T, ID, C> get(@NotNull String name) {
        return (RepositoryAdapter<T, ID, C>) ADAPTERS.get(name);
    }

    /**
     * Retrieves a repository adapter by entity type.
     *
     * @param entityType the entity class
     * @param <T> the entity type
     * @param <ID> the ID type
     * @param <C> the connection type
     * @return the adapter, or null if not found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T, ID, C> RepositoryAdapter<T, ID, C> get(@NotNull Class<T> entityType) {
        String name = TYPE_TO_ADAPTER.get(entityType);
        return name != null ? (RepositoryAdapter<T, ID, C>) ADAPTERS.get(name) : null;
    }

    /**
     * Retrieves the adapter name for a given entity type.
     *
     * @param entityType the entity class
     * @return the adapter name, or null if not found
     */
    @Nullable
    public static String getAdapterName(@NotNull Class<?> entityType) {
        return TYPE_TO_ADAPTER.get(entityType);
    }

    /**
     * Unregisters an adapter by name.
     *
     * @param name the adapter name
     * @return true if the adapter was removed, false if it didn't exist
     */
    public static boolean unregister(@NotNull String name) {
        @SuppressWarnings("resource")
        RepositoryAdapter<?, ?, ?> removed = ADAPTERS.remove(name);

        if (removed != null) {
            TYPE_TO_ADAPTER.remove(removed.getElementType());
            return true;
        }
        return false;
    }

    /**
     * Checks if an adapter with the given name is registered.
     *
     * @param name the adapter name
     * @return true if registered, false otherwise
     */
    public static boolean isRegistered(@NotNull String name) {
        return ADAPTERS.containsKey(name);
    }

    /**
     * Clears all registered adapters.
     * <p>
     * <b>Warning:</b> This should only be used for testing or cleanup purposes.
     */
    public static void clear() {
        ADAPTERS.clear();
        TYPE_TO_ADAPTER.clear();
    }

    /**
     * Returns the number of registered adapters.
     *
     * @return the adapter count
     */
    public static int size() {
        return ADAPTERS.size();
    }
}
