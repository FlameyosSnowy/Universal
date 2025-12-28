package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata.getMetadata;

/**
 * Abstract portable implementation for all backends.
 * Concrete classes only need to implement collection handling methods.
 */
@SuppressWarnings("unchecked")
public abstract class AbstractRelationshipHandler<T, ID, R> implements RelationshipHandler<ID> {
    protected final RepositoryInformation repositoryInformation;
    protected final Class<ID> idClass;
    protected final TypeResolverRegistry resolverRegistry;

    private static final Map<String, String> nameCache = new ConcurrentHashMap<>(16);

    // Relationship cache: "EntityType:ID:fieldName" -> cached result
    private final Map<String, Object> relationshipCache = new ConcurrentHashMap<>(64);

    protected AbstractRelationshipHandler(RepositoryInformation repositoryInformation,
                                          Class<ID> idClass,
                                          TypeResolverRegistry resolverRegistry) {
        this.repositoryInformation = repositoryInformation;
        this.idClass = idClass;
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    public @Nullable Object handleManyToOneRelationship(ID primaryKeyValue, @NotNull FieldData<?> field) {
        RepositoryInformation parentInfo = Objects.requireNonNull(getMetadata(field.type()), "Unknown repository for type " + field.type());
        String cacheKey = buildCacheKey(field, primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, parentInfo);
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + parentInfo.getType());

        FieldData<?> primaryKey = parentInfo.getPrimaryKey();
        if (primaryKey == null) throw new IllegalStateException("Missing primary key for " + parentInfo.getType());

        SelectQuery query = Query.select()
                .where(primaryKey.name(), primaryKeyValue)
                .limit(1)
                .build();

        List<Object> result = adapter.find(query);
        Object value = result.isEmpty() ? null : result.getFirst();
        relationshipCache.put(cacheKey, value == null ? NULL_MARKER : value);
        return value;
    }

    @Override
    public @Nullable Object handleOneToOneRelationship(ID primaryKeyValue, @NotNull FieldData<?> field) {
        String cacheKey = buildCacheKey(field, primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        // The field type is the "target" side (e.g. Warp for Faction.warp)
        Class<?> targetType = field.type();
        RepositoryInformation targetInfo = Objects.requireNonNull(
                getMetadata(targetType),
                "Unknown repository for type " + targetType
        );

        // Find the back-reference on the target that points back to this repository type,
        // e.g. Warp.faction when resolving Faction.warp
        OneToOneField link = getOneToOneField(targetInfo, repositoryInformation);
        if (link == null) {
            Logging.error("No OneToOne back-reference from " + targetInfo.getRepositoryName() +
                    " to " + repositoryInformation.getRepositoryName() + " for field " + field.name());
            relationshipCache.put(cacheKey, NULL_MARKER);
            return null;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, targetInfo);
        if (adapter == null) {
            Logging.error("Missing adapter for type: " + targetType.getName());
            relationshipCache.put(cacheKey, NULL_MARKER);
            return null;
        }

        return OneToOneLazyProxy.createOrFetch(
            field,
            primaryKeyValue,
            link,
            adapter,
            cacheKey,
            () -> relationshipCache.put(cacheKey, NULL_MARKER) // Cache setter
        );
    }

    @Override
    public List<Object> handleOneToManyRelationship(FieldData<?> field, ID primaryKeyValue) {
        // Check cache first
        String cacheKey = buildCacheKey(field, primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) {
            return (List<Object>) cached;
        }

        Class<?> mappedBy = field.oneToMany().mappedBy();
        RepositoryInformation relatedRepoInfo = Objects.requireNonNull(getMetadata(mappedBy), "Unknown repository for type " + mappedBy);

        String relationName = getRelationName(relatedRepoInfo, repositoryInformation.getType());

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapter(field, relatedRepoInfo);
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getType());

        if (!field.oneToMany().lazy()) return getResult(primaryKeyValue, adapter, relationName, cacheKey);
        return new LazyArrayList<>(() -> getResult(primaryKeyValue, adapter, relationName, cacheKey));
    }

    private List<Object> getResult(ID primaryKeyValue, @NotNull RepositoryAdapter<Object, Object, ?> adapter, String relationName, String cacheKey) {
        List<Object> result = adapter.find(Query.select().where(relationName, primaryKeyValue).build());
        List<Object> immutable = result == null ? Collections.emptyList() : List.copyOf(result);
        relationshipCache.put(cacheKey, immutable);
        return immutable;
    }

    // ---- Helper methods ---- //
    @Nullable
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

    /**
     * Find the field name on `info` that references parentType. This is the usual "mapped
     * by" side for one-to-many relationships.
     */
    protected static String getRelationName(@NotNull RepositoryInformation info, @NotNull Class<?> parentType) {
        // Use cache key composed of repo + parent type to avoid collisions
        String cacheKey = info.getRepositoryName() + "#" + parentType.getName();
        FieldData<?> primaryKey = info.getPrimaryKey();
        if (primaryKey == null) throw new IllegalStateException("Missing primary key for " + info.getType());

        return nameCache.computeIfAbsent(cacheKey, k -> {
            for (FieldData<?> field : info.getFields()) {
                if (field.type() == parentType)
                    return field.name();
            }
            // If not found, fall back to primary key (safeguard) — caller should probably fail earlier
            Logging.deepInfo("Relation name for parent type " + parentType.getName() + " not found in " + info.getRepositoryName());
            return primaryKey.name();
        });
    }

    // ---- Cache Management ---- //

    private static final Object NULL_MARKER = new Object();

    /**
     * Builds a cache key for relationship caching.
     */
    @NotNull
    private String buildCacheKey(@NotNull FieldData<?> field, Object id) {
        return repositoryInformation.getType().getSimpleName() + ":" + id + ":" + field.name();
    }

    /**
     * Resolves the appropriate adapter for a field, supporting both local and external repositories.
     *
     * @param field the field containing relationship information
     * @param targetInfo the target repository information
     * @return the resolved adapter, or null if not found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static RepositoryAdapter<Object, Object, ?> resolveAdapter(@NotNull FieldData<?> field, @NotNull RepositoryInformation targetInfo) {
        if (field.isExternalRelationship()) {
            String adapterName = field.externalRepository().adapter();
            RepositoryAdapter<Object, Object, ?> externalAdapter = RepositoryRegistry.get(adapterName);

            if (externalAdapter == null) {
                Logging.error("External adapter '" + adapterName + "' not found in RepositoryRegistry for field " + field.name());
                return null;
            }

            Logging.deepInfo("Using external adapter '" + adapterName + "' for field " + field.name());
            return externalAdapter;
        }

        return (RepositoryAdapter<Object, Object, ?>) RepositoryRegistry.get(targetInfo.getType());
    }
}