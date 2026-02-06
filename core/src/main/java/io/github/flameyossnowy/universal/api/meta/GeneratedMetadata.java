package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for generated repository metadata.
 * <p>
 * This class is populated at application startup by the generated registration classes.
 * Each entity annotated with @Repository will have a corresponding RepositoryModel
 * registered here by table name.
 */
public final class GeneratedMetadata {
    
    private static final Map<String, RepositoryModel> BY_TABLE_NAME = new ConcurrentHashMap<>();
    private static final Map<String, RepositoryModel> BY_ENTITY_CLASS = new ConcurrentHashMap<>();
    
    private GeneratedMetadata() {
        throw new AssertionError("No instances");
    }
    
    /**
     * Registers a repository model by its table name.
     * <p>
     * This method is called by generated registration classes during static initialization.
     *
     * @param tableName The table name
     * @param model     The repository model
     */
    public static void add(String tableName, RepositoryModel model) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be null or blank");
        }
        if (model == null) {
            throw new IllegalArgumentException("RepositoryModel cannot be null");
        }
        
        BY_TABLE_NAME.put(tableName, model);
        BY_ENTITY_CLASS.put(model.getEntityClass().getName(), model);
        System.out.println(tableName);
    }
    
    /**
     * Gets a repository model by table name.
     *
     * @param tableName The table name
     * @return The repository model, or null if not found
     */
    @Nullable
    public static RepositoryModel get(String tableName) {
        System.out.println(tableName);
        return BY_TABLE_NAME.get(tableName);
    }
    
    /**
     * Gets a repository model by entity class name.
     *
     * @param entityClassName The fully qualified entity class name
     * @return The repository model, or null if not found
     */
    @Nullable
    public static RepositoryModel getByEntityClass(String entityClassName) {
        return BY_ENTITY_CLASS.get(entityClassName);
    }
    
    /**
     * Gets a repository model by entity class.
     *
     * @param entityClass The entity class
     * @return The repository model, or null if not found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T, ID> RepositoryModel<T, ID> getByEntityClass(Class<T> entityClass) {
        System.out.println(BY_ENTITY_CLASS);
        System.out.println(entityClass.getName());
        return (RepositoryModel<T, ID>) BY_ENTITY_CLASS.get(entityClass.getName());
    }
    
    /**
     * Checks if a repository model exists for the given table name.
     *
     * @param tableName The table name
     * @return true if a model exists, false otherwise
     */
    public static boolean has(String tableName) {
        return BY_TABLE_NAME.containsKey(tableName);
    }
    
    /**
     * Checks if a repository model exists for the given entity class.
     *
     * @param entityClass The entity class
     * @return true if a model exists, false otherwise
     */
    public static boolean hasByEntityClass(Class<?> entityClass) {
        return BY_ENTITY_CLASS.containsKey(entityClass.getName());
    }
    
    /**
     * Gets all registered table names.
     *
     * @return An unmodifiable set of table names
     */
    public static java.util.Set<String> getTableNames() {
        return java.util.Collections.unmodifiableSet(BY_TABLE_NAME.keySet());
    }
    
    /**
     * Gets all registered repository models.
     *
     * @return An unmodifiable collection of repository models
     */
    public static java.util.Collection<RepositoryModel> getAll() {
        return java.util.Collections.unmodifiableCollection(BY_TABLE_NAME.values());
    }
    
    /**
     * Clears all registered metadata.
     * <p>
     * This method should only be used for testing purposes.
     */
    public static void clear() {
        BY_TABLE_NAME.clear();
        BY_ENTITY_CLASS.clear();
    }
}