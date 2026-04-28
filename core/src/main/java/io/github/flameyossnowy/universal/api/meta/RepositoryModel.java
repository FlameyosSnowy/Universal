package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Represents metadata about a repository/entity.
 * Generic types:
 * - T: The entity type
 * - ID: The primary key type
 */
public interface RepositoryModel<T, ID> {
    String packageName();

    String entitySimpleName();

    String entityQualifiedName();

    String tableName();

    boolean isRecord();

    List<FieldModel<T>> fields();

    List<String> primaryKeys();

    List<IndexModel> indexes();

    List<ConstraintModel> constraints();

    FieldModel<T> fieldByName(String name);

    default FieldModel<T> columnFieldByName(String columnName) {
        if (columnName == null) {
            return null;
        }
        for (FieldModel<T> field : fields()) {
            if (field == null) continue;
            String col = field.columnName();
            if (col != null && col.equalsIgnoreCase(columnName)) {
                return field;
            }
        }
        return null;
    }

    FieldModel<T> getPrimaryKey();

    ID getPrimaryKeyValue(T entity);

    void setPrimaryKeyValue(T entity, ID id);

    List<RelationshipModel<T, ID>> getRelationships();

    Map<String, FieldModel<T>> getOneToOneCache();

    Map<String, FieldModel<T>> getOneToManyCache();

    Map<String, FieldModel<T>> getManyToOneCache();

    int getFetchPageSize();

    Class<T> getEntityClass();

    Class<ID> getIdClass();

    /**
     * Repository-level exception handler.
     * Return null to use DefaultExceptionHandler.
     */
    ExceptionHandler<T, ID, ?> getExceptionHandler();

    /**
     * Entity lifecycle listener (insert/update/delete hooks).
     */
    EntityLifecycleListener<T> getEntityLifecycleListener();

    /**
     * Audit logger for this repository.
     */
    AuditLogger<T> getAuditLogger();

    /**
     * Whether this repository is annotated with @Cacheable
     */
    boolean isCacheable();

    /**
     * Cache configuration if @Cacheable is present
     */
    CacheConfig getCacheConfig();

    /**
     * Whether this repository is annotated with @GlobalCacheable
     * (initializes session cache on application startup)
     */
    boolean isGlobalCacheable();

    default boolean hasRelationships() {
        return !getRelationships().isEmpty();
    }

    /**
     * Returns list of TypeResolver suppliers that must be registered
     * for this repository to function properly.
     * This avoids reflection-based instantiation at runtime.
     */
    default List<Supplier<TypeResolver<?>>> getRequiredResolvers() {
        return List.of();
    }

    /**
     * Returns the credentials provider for network repositories, or null if not configured.
     * This avoids reflection-based instantiation at runtime.
     */
    default Supplier<String> credentialsProvider() {
        return null;
    }

    default SessionCache<ID, T> createGlobalSessionCache() {
        return null;
    }

    default List<FieldModel<T>> getJsonFields() {
        List<FieldModel<T>> fields = fields();
        List<FieldModel<T>> list = new ArrayList<>(fields.size());
        for (FieldModel<T> tFieldModel : fields) {
            if (tFieldModel.isJson()) {
                list.add(tFieldModel);
            }
        }
        return list;
    }

    /**
     * Whether this repository uses any JSON features.
     */
    default boolean usesJsonStorage() {
        return !getJsonFields().isEmpty();
    }

    /**
     * Pre-built mapping from parameter names (including JSON path expressions)
     * to their base column names. Used for O(1) parameter index lookup at runtime.
     * Generated at compile time to avoid string parsing in the hot path.
     *
     * @return unmodifiable map of parameter name -> column name, or empty map if none
     */
    default Map<String, String> getParameterNameMappings() {
        return Map.of();
    }

    /**
     * Pre-built mapping from relationship kind to fields of that kind.
     * Used for O(1) relationship field lookup in prefetch operations.
     * Generated at compile time to avoid runtime filtering.
     *
     * @return unmodifiable map of RelationshipKind -> list of fields, or empty map if none
     */
    default Map<RelationshipKind, List<FieldModel<T>>> getFieldIndexes() {
        return Map.of();
    }
}