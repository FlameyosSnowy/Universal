package io.github.flameyossnowy.universal.checker;

import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.SessionCache;

import javax.lang.model.type.TypeMirror;
import java.util.List;

public record RepositoryModel(
    // Identity
    String packageName,
    String entitySimpleName,
    String entityQualifiedName,
    String tableName,
    boolean isRecord,

    TypeMirror entityType,
    TypeMirror idType,

    // Structure
    List<FieldModel> fields,
    List<FieldModel> primaryKeys,
    List<IndexModel> indexes,
    List<ConstraintModel> constraints,
    List<RelationshipModel> relationships,

    // Configuration
    int fetchPageSize,

    // Caching Metadata
    boolean cacheable,
    CacheConfig cacheConfig,
    boolean globalCacheable,
    Class<? extends SessionCache<?, ?>> sessionCache,

    TypeMirror auditLoggerType,              // nullable
    TypeMirror exceptionHandlerType,          // nullable
    TypeMirror entityLifecycleListenerType    // nullable
) {
    public boolean hasAuditLogger() {
        return auditLoggerType != null;
    }

    public boolean hasExceptionHandler() {
        return exceptionHandlerType != null;
    }

    public boolean hasLifecycleListener() {
        return entityLifecycleListenerType != null;
    }

    public String auditLoggerQualifiedName() {
        return auditLoggerType == null ? null : auditLoggerType.toString();
    }

    public String exceptionHandlerQualifiedName() {
        return exceptionHandlerType == null ? null : exceptionHandlerType.toString();
    }

    public String lifecycleListenerQualifiedName() {
        return entityLifecycleListenerType == null ? null : entityLifecycleListenerType.toString();
    }
}