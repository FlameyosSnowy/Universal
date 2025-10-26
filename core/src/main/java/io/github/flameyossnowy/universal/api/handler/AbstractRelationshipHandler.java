package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
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
public abstract class AbstractRelationshipHandler<T, ID, R> implements RelationshipHandler<T, ID, R> {
    protected final RepositoryInformation repositoryInformation;
    protected final Class<ID> idClass;
    protected final Map<Class<?>, RepositoryAdapter<?, ?, ?>> repositories;
    protected final TypeResolverRegistry resolverRegistry;

    private static final Map<String, String> nameCache = new ConcurrentHashMap<>(16);
    
    // Relationship cache: "EntityType:ID:fieldName" -> cached result
    private final Map<String, Object> relationshipCache = new ConcurrentHashMap<>(64);

    protected AbstractRelationshipHandler(RepositoryInformation repositoryInformation,
                                          Class<ID> idClass,
                                          Map<Class<?>, RepositoryAdapter<?, ?, ?>> repositories, TypeResolverRegistry resolverRegistry) {
        this.repositoryInformation = repositoryInformation;
        this.idClass = idClass;
        this.repositories = repositories;
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    public Object handleManyToOneRelationship(Object relationKey, @NotNull FieldData<?> field) {
        RepositoryInformation parentInfo = Objects.requireNonNull(getMetadata(field.type()), "Unknown repository for type " + field.type());
        String cacheKey = buildCacheKey(field, relationKey);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

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
    public Object handleOneToOneRelationship(ID primaryKeyValue, @NotNull FieldData<?> field) {
        RepositoryInformation targetInfo = Objects.requireNonNull(getMetadata(field.type()), "Unknown repository for type " + field.type());
        String cacheKey = buildCacheKey(field, primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        OneToOneField link = getOneToOneField(targetInfo, repositoryInformation);
        if (link == null) {
            Logging.error("No link found for " + field.name() + " in " + repositoryInformation.getRepositoryName());
            return null;
        }

        RepositoryAdapter<Object, Object, ?> adapter =
                (RepositoryAdapter<Object, Object, ?>) repositories.get(targetInfo.getType());
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + targetInfo.getType());

        SelectQuery query = Query.select().where(link.name(), primaryKeyValue).limit(1).build();

        Object value = adapter.first(query);
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

        Class<?> mappedBy = field.oneToMany().mappedBy();
        RepositoryInformation relatedRepoInfo = Objects.requireNonNull(getMetadata(mappedBy), "Unknown repository for type " + mappedBy);

        String relationName = getRelationName(relatedRepoInfo);

        RepositoryAdapter<Object, Object, ?> adapter = (RepositoryAdapter<Object, Object, ?>)
                Objects.requireNonNull(repositories.get(relatedRepoInfo.getType()), "Missing adapter for " + relatedRepoInfo.getType());

        if (!field.oneToMany().lazy()) return getResult(primaryKeyValue, adapter, relationName, cacheKey);
        return new LazyArrayList<>(() -> getResult(primaryKeyValue, adapter, relationName, cacheKey));
    }

    private List<Object> getResult(ID primaryKeyValue, @NotNull RepositoryAdapter<Object, Object, ?> adapter, String relationName, String cacheKey) {
        List<Object> result = adapter.find(Query.select().where(relationName, primaryKeyValue).build());
        relationshipCache.put(cacheKey, result);
        return result;
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

    protected static String getRelationName(@NotNull RepositoryInformation info) {
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
    @NotNull
    private String buildCacheKey(@NotNull FieldData<?> field, Object id) {
        return repositoryInformation.getType().getSimpleName() + ":" + id + ":" + field.name();
    }
}
