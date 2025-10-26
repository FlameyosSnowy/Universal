package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Abstract portable implementation for all backends.
 * Concrete classes only need to implement collection handling methods.
 */
@SuppressWarnings("unchecked")
public abstract class AbstractRelationshipHandler<T, ID, R> implements RelationshipHandler<T, ID, R> {
    protected final RepositoryInformation repositoryInformation;
    protected final Class<ID> idClass;
    protected final Map<Class<?>, RepositoryAdapter<?, ?, ?>> repositories;
    protected final TypeResolverRegistry resolverRegistry;

    private static final Map<String, String> nameCache = new ConcurrentHashMap<>(16);
    
    // Relationship cache: "EntityType:ID:fieldName" -> cached result
    private final Map<String, Object> relationshipCache = new ConcurrentHashMap<>(64);
    
    // Batch loading threshold
    private static final int BATCH_THRESHOLD = 50;

    protected AbstractRelationshipHandler(RepositoryInformation repositoryInformation,
                                          Class<ID> idClass,
                                          Map<Class<?>, RepositoryAdapter<?, ?, ?>> repositories, TypeResolverRegistry resolverRegistry) {
        this.repositoryInformation = repositoryInformation;
        this.idClass = idClass;
        this.repositories = repositories;
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    public Object handleManyToOneRelationship(Object relationKey, FieldData<?> field) {
        RepositoryInformation parentInfo = RepositoryMetadata.getMetadata(field.type());
        if (parentInfo == null) throw new IllegalStateException("Unknown repository for type " + field.type());

        // Check cache first
        String cacheKey = buildCacheKey(field, relationKey);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) {
            return cached == NULL_MARKER ? null : cached;
        }

        RepositoryAdapter<Object, Object, ?> adapter =
                (RepositoryAdapter<Object, Object, ?>) repositories.get(parentInfo.getType());
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + parentInfo.getType());

        SelectQuery query = Query.select()
                .where(parentInfo.getPrimaryKey().name(), relationKey)
                .limit(1)
                .build();

        List<Object> result = adapter.find(query);
        Object value = result.isEmpty() ? null : result.get(0);
        relationshipCache.put(cacheKey, value == null ? NULL_MARKER : value);
        return value;
    }

    @Override
    public Object handleOneToOneRelationship(ID primaryKeyValue, FieldData<?> field) {
        RepositoryInformation targetInfo = RepositoryMetadata.getMetadata(field.type());
        if (targetInfo == null) throw new IllegalStateException("Unknown repository for type " + field.type());

        // Check cache first
        String cacheKey = buildCacheKey(field, primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) {
            return cached == NULL_MARKER ? null : cached;
        }

        OneToOneField link = getOneToOneField(targetInfo, repositoryInformation);
        if (link == null) {
            Logging.error("No link found for " + field.name() + " in " + repositoryInformation.getRepositoryName());
            return null;
        }

        RepositoryAdapter<Object, Object, ?> adapter =
                (RepositoryAdapter<Object, Object, ?>) repositories.get(targetInfo.getType());
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + targetInfo.getType());

        SelectQuery query = Query.select()
                .where(link.name(), primaryKeyValue)
                .limit(1)
                .build();

        List<Object> result = adapter.find(query);
        Object value = result.isEmpty() ? null : result.get(0);
        relationshipCache.put(cacheKey, value == null ? NULL_MARKER : value);
        return value;
    }

    @Override
    public List<Object> handleOneToManyRelationship(FieldData<?> field, ID primaryKeyValue) {
        // Check cache first
        String cacheKey = buildCacheKey(field, primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) {
            return (List<Object>) cached;
        }
        
        RepositoryInformation relatedRepoInfo =
                RepositoryMetadata.getMetadata(field.oneToMany().mappedBy());
        if (relatedRepoInfo == null)
            throw new IllegalStateException("Unknown repository for type " + field.oneToMany().mappedBy());

        String relationName = getRelationName(relatedRepoInfo);

        RepositoryAdapter<Object, Object, ?> adapter =
                (RepositoryAdapter<Object, Object, ?>) repositories.get(relatedRepoInfo.getType());
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getType());

        if (!field.oneToMany().lazy()) return getResult(primaryKeyValue, adapter, relationName, cacheKey);

        return new LazyArrayList<>(() -> getResult(primaryKeyValue, adapter, relationName, cacheKey));
    }

    private List<Object> getResult(ID primaryKeyValue, RepositoryAdapter<Object, Object, ?> adapter, String relationName, String cacheKey) {
        List<Object> result = adapter.find(Query.select().where(relationName, primaryKeyValue).build());
        relationshipCache.put(cacheKey, result);
        return result;
    }

    // ---- Helper methods ---- //

    public static OneToOneField getOneToOneField(
            @NotNull RepositoryInformation targetInfo,
            RepositoryInformation repoInfo
    ) {
        for (FieldData<?> relatedField : targetInfo.getFields()) {
            if (relatedField.type() == repoInfo.getType())
                return new OneToOneField(relatedField.name(), relatedField);
        }
        return null;
    }

    protected static String getRelationName(RepositoryInformation info) {
        return nameCache.computeIfAbsent(info.getRepositoryName(), k -> {
            for (FieldData<?> field : info.getFields()) {
                if (field.type() == info.getType())
                    return field.name();
            }
            return null;
        });
    }
    
    // ---- Cache Management ---- //
    
    private static final Object NULL_MARKER = new Object();
    
    /**
     * Builds a cache key for relationship caching.
     */
    private String buildCacheKey(FieldData<?> field, Object id) {
        return repositoryInformation.getType().getSimpleName() + ":" + id + ":" + field.name();
    }
    
    /**
     * Invalidates all cached relationships for a specific entity.
     */
    public void invalidateCache(ID id) {
        String prefix = repositoryInformation.getType().getSimpleName() + ":" + id + ":";
        relationshipCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    /**
     * Clears all cached relationships.
     */
    public void clearCache() {
        relationshipCache.clear();
    }
    
    /**
     * Batch loads OneToMany relationships for multiple entities.
     * This eliminates N+1 query problem by using IN clause.
     */
    public Map<ID, List<Object>> batchLoadOneToMany(FieldData<?> field, List<ID> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        
        RepositoryInformation relatedRepoInfo =
                RepositoryMetadata.getMetadata(field.oneToMany().mappedBy());
        if (relatedRepoInfo == null)
            throw new IllegalStateException("Unknown repository for type " + field.oneToMany().mappedBy());

        String relationName = getRelationName(relatedRepoInfo);

        RepositoryAdapter<Object, Object, ?> adapter =
                (RepositoryAdapter<Object, Object, ?>) repositories.get(relatedRepoInfo.getType());
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getType());

        // Execute single query with IN clause
        List<Object> allResults = adapter.find(
            Query.select().whereIn(relationName, new ArrayList<>(ids)).build()
        );
        
        // Group results by foreign key
        Map<ID, List<Object>> grouped = new HashMap<>();
        for (Object result : allResults) {
            try {
                FieldData<?> fkField = relatedRepoInfo.getField(relationName);
                if (fkField != null) {
                    ID fkValue = (ID) fkField.getValue(result);
                    grouped.computeIfAbsent(fkValue, k -> new ArrayList<>()).add(result);
                }
            } catch (Exception e) {
                Logging.error("Error grouping batch results: " + e.getMessage());
            }
        }
        
        // Cache all results
        for (ID id : ids) {
            List<Object> results = grouped.getOrDefault(id, Collections.emptyList());
            String cacheKey = buildCacheKey(field, id);
            relationshipCache.put(cacheKey, results);
        }
        
        return grouped;
    }
    
    /**
     * Batch loads ManyToOne relationships for multiple foreign keys.
     */
    public Map<Object, Object> batchLoadManyToOne(FieldData<?> field, List<Object> foreignKeys) {
        if (foreignKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // Remove duplicates
        List<Object> uniqueKeys = foreignKeys.stream().distinct().collect(Collectors.toList());
        
        RepositoryInformation parentInfo = RepositoryMetadata.getMetadata(field.type());
        if (parentInfo == null)
            throw new IllegalStateException("Unknown repository for type " + field.type());

        RepositoryAdapter<Object, Object, ?> adapter =
                (RepositoryAdapter<Object, Object, ?>) repositories.get(parentInfo.getType());
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + parentInfo.getType());

        // Execute single query with IN clause
        List<Object> allResults = adapter.find(
            Query.select().whereIn(parentInfo.getPrimaryKey().name(), uniqueKeys).build()
        );
        
        // Map results by primary key
        Map<Object, Object> resultMap = new HashMap<>();
        for (Object result : allResults) {
            try {
                Object pkValue = parentInfo.getPrimaryKey().getValue(result);
                resultMap.put(pkValue, result);
            } catch (Exception e) {
                Logging.error("Error mapping batch results: " + e.getMessage());
            }
        }
        
        // Cache all results
        for (Object fk : uniqueKeys) {
            Object result = resultMap.get(fk);
            String cacheKey = buildCacheKey(field, fk);
            relationshipCache.put(cacheKey, result == null ? NULL_MARKER : result);
        }
        
        return resultMap;
    }
}
