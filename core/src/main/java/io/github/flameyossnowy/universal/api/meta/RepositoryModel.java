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
     * Returns list of TypeResolver classes that must be registered
     * for this repository to function properly.
     */
    default List<Class<? extends TypeResolver<?>>> getRequiredResolvers() {
        return List.of();
    }

    default SessionCache<ID, T> createGlobalSessionCache() {
        return null;
    }

    default List<FieldModel<T>> getJsonFields() {
        List<FieldModel<T>> list = new ArrayList<>();
        for (FieldModel<T> tFieldModel : fields()) {
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
}