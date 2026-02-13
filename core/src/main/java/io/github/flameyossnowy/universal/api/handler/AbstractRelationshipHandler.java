package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract portable implementation for all backends.
 * Concrete classes only need to implement collection handling methods.
 */
@SuppressWarnings("unchecked")
public abstract class AbstractRelationshipHandler<T, ID> implements RelationshipHandler<T, ID> {
    protected final RepositoryModel<T, ID> repositoryModel;
    protected final Class<ID> idClass;
    protected final TypeResolverRegistry resolverRegistry;

    private static final Map<String, String> nameCache = new ConcurrentHashMap<>(16);

    // Relationship cache: "EntityType:ID:fieldName" -> cached result
    private final Map<String, Object> relationshipCache = new ConcurrentHashMap<>(64);

    private static final Object NULL_MARKER = new Object();

    private final String entityPrefix;

    protected AbstractRelationshipHandler(
        RepositoryModel<T, ID> repositoryModel,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry
    ) {
        this.repositoryModel = repositoryModel;
        this.idClass = idClass;
        this.resolverRegistry = resolverRegistry;
        this.entityPrefix = repositoryModel.entitySimpleName() + ":";
    }

    @Override
    public @Nullable Object handleManyToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        RepositoryModel<?, ?> parentInfo = GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null) {
            throw new IllegalStateException("Unknown repository for type " + field.type());
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, parentInfo);
        if (adapter == null) {
            throw new IllegalStateException("Missing adapter for " + parentInfo.getEntityClass());
        }

        SelectQuery query = Query.select()
            .where(repositoryModel.getPrimaryKey().columnName()).eq(primaryKeyValue)
            .limit(1)
            .build();

        List<Object> result = adapter.find(query);
        Object value = result.isEmpty() ? null : result.getFirst();
        relationshipCache.put(cacheKey, value == null ? NULL_MARKER : value);
        return value;
    }

    @Override
    public @Nullable Object handleOneToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        // The field type is the "target" side (e.g. Warp for Faction.warp)
        Class<?> targetType = field.type();
        RepositoryModel<?, ?> targetInfo = GeneratedMetadata.getByEntityClass(targetType);
        if (targetInfo == null) {
            throw new IllegalStateException("Unknown repository for type " + targetType);
        }

        // Find the back-reference on the target that points back to this repository type
        FieldModel<?> backRef = findOneToOneBackReference(targetInfo, repositoryModel.getEntityClass());
        if (backRef == null) {
            Logging.error("No OneToOne back-reference from " + targetInfo.tableName() +
                " to " + repositoryModel.tableName() + " for field " + field.name());
            relationshipCache.put(cacheKey, NULL_MARKER);
            return null;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, targetInfo);
        if (adapter == null) {
            Logging.error("Missing adapter for type: " + targetType.getName());
            relationshipCache.put(cacheKey, NULL_MARKER);
            return null;
        }

        SelectQuery query = Query.select()
            .where(backRef.columnName()).eq(primaryKeyValue)
            .limit(1)
            .build();

        List<Object> results = adapter.find(query);
        Object result = (results == null || results.isEmpty()) ? null : results.getFirst();

        relationshipCache.put(cacheKey, result == null ? NULL_MARKER : result);

        return result;
    }

    @Override
    public List<Object> handleOneToManyRelationship(ID primaryKeyValue, FieldModel<T> field) {
        // Check cache first
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) {
            return (List<Object>) cached;
        }

        // Get the target entity type from the field's element type
        Class<?> targetType = field.elementType();
        if (targetType == null) {
            throw new IllegalStateException("OneToMany field must have elementType: " + field.name());
        }

        RepositoryModel<?, ?> relatedRepoInfo = GeneratedMetadata.getByEntityClass(targetType);
        if (relatedRepoInfo == null) {
            throw new IllegalStateException("Unknown repository for type " + targetType);
        }

        // Find the back-reference field name
        String relationName = findManyToOneFieldName(relatedRepoInfo, repositoryModel.getEntityClass());
        if (relationName == null) {
            throw new IllegalStateException(
                "No ManyToOne back-reference found in " + targetType.getSimpleName() +
                    " pointing to " + repositoryModel.getEntityClass().getSimpleName()
            );
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, relatedRepoInfo);
        if (adapter == null) {
            throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getEntityClass());
        }

        if (!field.lazy()) {
            return loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey);
        }

        // Lazy loading
        return new LazyArrayList<>(() ->
            loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey)
        );
    }

    private List<Object> loadOneToManyResults(
        ID primaryKeyValue,
        RepositoryAdapter<Object, Object, ?> adapter,
        String relationName,
        String cacheKey
    ) {
        List<Object> result = adapter.find(
            Query.select()
                .where(relationName).eq(primaryKeyValue)
                .build()
        );
        List<Object> immutable = result == null ? Collections.emptyList() : List.copyOf(result);
        relationshipCache.put(cacheKey, immutable);
        return immutable;
    }

    // ========== Helper Methods ==========

    /**
     * Find the OneToOne back-reference field in the target repository
     * that points back to the source entity type.
     */
    @Nullable
    private static FieldModel<?> findOneToOneBackReference(
        @NotNull RepositoryModel<?, ?> targetInfo,
        @NotNull Class<?> sourceEntityType
    ) {
        for (FieldModel<?> field : targetInfo.getOneToOneCache().values()) {
            if (sourceEntityType.isAssignableFrom(field.type())) {
                return field;
            }
        }
        return null;
    }

    /**
     * Find the ManyToOne field name in the target repository
     * that points back to the parent type.
     */
    @Nullable
    private static String findManyToOneFieldName(
        @NotNull RepositoryModel<?, ?> targetInfo,
        @NotNull Class<?> parentType
    ) {
        String cacheKey = (targetInfo.tableName() + "#" + parentType.getName()).intern();

        return nameCache.computeIfAbsent(cacheKey, k -> {
            for (FieldModel<?> field : targetInfo.getManyToOneCache().values()) {
                if (field.type() == parentType) {
                    return field.columnName();
                }
            }

            // If not found, log and return null
            Logging.deepInfo(() ->
                "ManyToOne field for parent type " + parentType.getName() +
                    " not found in " + targetInfo.tableName()
            );
            return null;
        });
    }

    /**
     * Resolves the appropriate adapter for a field, supporting both local and external repositories.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static RepositoryAdapter<Object, Object, ?> resolveAdapter(
        @NotNull FieldModel<?> field,
        @NotNull RepositoryModel<?, ?> targetInfo
    ) {
        String adapterName = field.externalRepository();
        if (adapterName != null) {
            RepositoryAdapter<Object, Object, ?> externalAdapter = RepositoryRegistry.get(adapterName);

            if (externalAdapter == null) {
                Logging.error(
                    "External adapter '" + adapterName +
                        "' not found in RepositoryRegistry for field " + field.name()
                );
                return null;
            }

            Logging.deepInfo(() -> "Using external adapter '" + adapterName + "' for field " + field.name());
            return externalAdapter;
        }

        return (RepositoryAdapter<Object, Object, ?>) RepositoryRegistry.get(targetInfo.getEntityClass());
    }

    // ========== Batch Loading (Prefetching) ==========

    private void batchLoadOneToOne(FieldModel<T> field, List<ID> parentIds) {
        RepositoryModel<?, ?> target = GeneratedMetadata.getByEntityClass(field.type());
        if (target == null) {
            throw new IllegalStateException("Unknown repository for type " + field.type());
        }

        FieldModel<Object> backRef = (FieldModel<Object>) findOneToOneBackReference(target, repositoryModel.getEntityClass());
        if (backRef == null) {
            Logging.error("No OneToOne back-reference for field: " + field.name());
            return;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, target);
        if (adapter == null) {
            Logging.error("No adapter found for type: " + field.type());
            return;
        }

        List<Object> results = adapter.find(
            Query.select()
                .where(backRef.columnName()).in(parentIds)
                .build()
        );

        Map<ID, Object> mapped = new HashMap<>();
        for (Object obj : results) {
            ID parentId = (ID) backRef.getValue(obj);
            if (mapped.put(parentId, obj) != null) {
                throw new IllegalStateException(
                    "Multiple one-to-one results for field " + field.name()
                );
            }
        }

        for (ID id : parentIds) {
            relationshipCache.put(
                buildCacheKey(field.name(), id),
                mapped.getOrDefault(id, NULL_MARKER)
            );
        }
    }

    private void batchLoadOneToMany(FieldModel<T> field, List<ID> parentIds) {
        Class<?> targetType = field.elementType();
        if (targetType == null) {
            throw new IllegalStateException("OneToMany field must have elementType: " + field.name());
        }

        RepositoryModel<?, ?> related = GeneratedMetadata.getByEntityClass(targetType);
        if (related == null) {
            throw new IllegalStateException("Unknown repository for type " + targetType);
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, related);
        if (adapter == null) {
            throw new IllegalStateException("No adapter found for type: " + targetType);
        }

        String relationName = findManyToOneFieldName(related, repositoryModel.getEntityClass());
        if (relationName == null) {
            throw new IllegalStateException(
                "No ManyToOne back-reference found for OneToMany field: " + field.name()
            );
        }

        List<Object> results = adapter.find(
            Query.select()
                .where(relationName).eq(parentIds)
                .build()
        );

        Map<ID, List<Object>> grouped = new HashMap<>(parentIds.size());
        FieldModel<Object> backRefField = (FieldModel<Object>) related.fieldByName(relationName);

        for (Object child : results) {
            ID parentId = (ID) backRefField.getValue(child);
            grouped.computeIfAbsent(parentId, k -> new ArrayList<>(16)).add(child);
        }

        for (ID id : parentIds) {
            relationshipCache.put(
                buildCacheKey(field.name(), id),
                List.copyOf(grouped.getOrDefault(id, List.of()))
            );
        }
    }

    @Override
    public void prefetch(Iterable<?> parents, Set<String> fields) {
        Map<FieldModel<T>, List<ID>> oneToMany = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> oneToOne = new HashMap<>(fields.size());

        for (Object parent : parents) {
            ID id = repositoryModel.getPrimaryKeyValue((T) parent);

            for (String fieldName : fields) {
                FieldModel<T> field = repositoryModel.fieldByName(fieldName);
                if (field == null || !field.relationship()) {
                    continue;
                }

                if (field.relationshipKind() == RelationshipKind.ONE_TO_MANY) {
                    oneToMany
                        .computeIfAbsent(field, f -> new ArrayList<>(16))
                        .add(id);
                } else if (field.relationshipKind() == RelationshipKind.ONE_TO_ONE) {
                    oneToOne
                        .computeIfAbsent(field, f -> new ArrayList<>(16))
                        .add(id);
                }
            }
        }

        oneToMany.forEach(this::batchLoadOneToMany);
        oneToOne.forEach(this::batchLoadOneToOne);
    }

    // ========== Cache Management ==========

    /**
     * Builds a cache key for relationship caching.
     */
    @NotNull
    private String buildCacheKey(@NotNull String fieldName, Object id) {
        return entityPrefix + id + ":" + fieldName;
    }

    @Override
    public void invalidateRelationshipsForId(@NotNull ID id) {
        String prefix = entityPrefix + id + ":";
        relationshipCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void clear() {
        relationshipCache.clear();
    }
}