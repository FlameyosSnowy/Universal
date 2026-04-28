package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.ReadPolicy;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.meta.RelationshipModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Abstract portable implementation for all backends with macro optimizations.
 * Concrete classes only need to implement collection handling methods.
 *
 * <h3>Optimization Features:</h3>
 * <ul>
 *   <li>Multi-level batch prefetching</li>
 *   <li>Tiered caching (L1 thread-local + L2 shared)</li>
 *   <li>Adapter and query result caching</li>
 *   <li>Configurable parallel batch loading</li>
 *   <li>Smart batch size auto-tuning</li>
 *   <li>Performance metrics and monitoring</li>
 * </ul>
 */
@SuppressWarnings({ "unchecked", "unused", "DuplicatedCode" })
public abstract class AbstractRelationshipHandler<T, ID> implements RelationshipHandler<T, ID> {

    /**
     * Configuration for relationship handler caches.
     * Use {@link #builder()} to create instances with custom settings.
     */
    public record CacheConfiguration(
        int relationshipCacheSize,
        int adapterCacheSize,
        int queryResultCacheSize,
        int cacheKeyPoolSize
    ) {
        public static final int DEFAULT_RELATIONSHIP_CACHE_SIZE = 10_000;
        public static final int DEFAULT_ADAPTER_CACHE_SIZE = 1_000;
        public static final int DEFAULT_QUERY_RESULT_CACHE_SIZE = 5_000;
        public static final int DEFAULT_CACHE_KEY_POOL_SIZE = 1024;

        public CacheConfiguration {
            if (relationshipCacheSize < 1)
                throw new IllegalArgumentException("relationshipCacheSize must be at least 1");
            if (adapterCacheSize < 1)
                throw new IllegalArgumentException("adapterCacheSize must be at least 1");
            if (queryResultCacheSize < 1)
                throw new IllegalArgumentException("queryResultCacheSize must be at least 1");
            if (cacheKeyPoolSize < 1)
                throw new IllegalArgumentException("cacheKeyPoolSize must be at least 1");
        }

        public CacheConfiguration() {
            this(DEFAULT_RELATIONSHIP_CACHE_SIZE, DEFAULT_ADAPTER_CACHE_SIZE,
                 DEFAULT_QUERY_RESULT_CACHE_SIZE, DEFAULT_CACHE_KEY_POOL_SIZE);
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder toBuilder() {
            return new Builder()
                .relationshipCacheSize(relationshipCacheSize)
                .adapterCacheSize(adapterCacheSize)
                .queryResultCacheSize(queryResultCacheSize)
                .cacheKeyPoolSize(cacheKeyPoolSize);
        }

        public static class Builder {
            private int relationshipCacheSize = DEFAULT_RELATIONSHIP_CACHE_SIZE;
            private int adapterCacheSize = DEFAULT_ADAPTER_CACHE_SIZE;
            private int queryResultCacheSize = DEFAULT_QUERY_RESULT_CACHE_SIZE;
            private int cacheKeyPoolSize = DEFAULT_CACHE_KEY_POOL_SIZE;

            private Builder() {}

            public Builder relationshipCacheSize(int size) {
                this.relationshipCacheSize = size;
                return this;
            }

            public Builder adapterCacheSize(int size) {
                this.adapterCacheSize = size;
                return this;
            }

            public Builder queryResultCacheSize(int size) {
                this.queryResultCacheSize = size;
                return this;
            }

            public Builder cacheKeyPoolSize(int size) {
                this.cacheKeyPoolSize = size;
                return this;
            }

            public CacheConfiguration build() {
                return new CacheConfiguration(relationshipCacheSize, adapterCacheSize,
                    queryResultCacheSize, cacheKeyPoolSize);
            }
        }
    }

    protected final RepositoryModel<T, ID> repositoryModel;
    protected final Class<ID> idClass;
    protected final TypeResolverRegistry resolverRegistry;

    private final ExecutorService parallelExecutor;

    // Static caches shared across all handlers - tunable via static methods
    private static volatile int nameCacheSize = 1_000;
    private static volatile int l1CacheInitialCapacity = 64;
    private static Map<String, String> nameCache = new ConcurrentLRUCache<>(nameCacheSize);

    private static final ThreadLocal<WeakReference<Map<String, Object>>> l1Cache =
        ThreadLocal.withInitial(() -> new WeakReference<>(new HashMap<>(l1CacheInitialCapacity)));

    // Instance caches - configured via CacheConfiguration in constructor
    private final Map<String, Object> relationshipCache;
    private final Map<String, RepositoryAdapter<Object, Object, ?>> adapterCache;
    private final Map<String, List<Object>> queryResultCache;
    private final int cacheKeyPoolSizeInstance;
    private final String[] cacheKeyPool;

    private static final int MIN_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 1000;
    private final Map<String, Integer> optimalBatchSizes = new ConcurrentHashMap<>(32);

    private final AtomicLong cacheMisses   = new AtomicLong();
    private final AtomicLong l1CacheHits   = new AtomicLong();
    private final AtomicLong l2CacheHits   = new AtomicLong();
    private final Map<String, AtomicLong> queryCountByField = new ConcurrentHashMap<>(32);

    private static final Object NULL_MARKER = new Object();

    private final String entityPrefix;

    private volatile boolean parallelPrefetchEnabled  = false;
    private volatile int     prefetchThreadPoolSize    = Runtime.getRuntime().availableProcessors();
    private volatile boolean autoWarmCache             = false;
    private volatile boolean autoDeepPrefetch          = false;
    private volatile int     autoDeepPrefetchDepth     = 2;

    private static final Pattern PATTERN = Pattern.compile("\\.");

    protected AbstractRelationshipHandler(
        RepositoryModel<T, ID> repositoryModel,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry
    ) {
        this(repositoryModel, idClass, resolverRegistry, new CacheConfiguration());
    }

    /**
     * Construct with custom cache configuration.
     *
     * @param repositoryModel the repository metadata
     * @param idClass the ID type class
     * @param resolverRegistry the type resolver registry
     * @param cacheConfig cache tuning configuration
     */
    protected AbstractRelationshipHandler(
        RepositoryModel<T, ID> repositoryModel,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry,
        CacheConfiguration cacheConfig
    ) {
        this.repositoryModel = repositoryModel;
        this.idClass         = idClass;
        this.resolverRegistry = resolverRegistry;
        this.cacheKeyPoolSizeInstance = cacheConfig.cacheKeyPoolSize();
        this.cacheKeyPool = new String[cacheKeyPoolSizeInstance];
        this.relationshipCache = new ConcurrentLRUCache<>(cacheConfig.relationshipCacheSize());
        this.adapterCache = new ConcurrentLRUCache<>(cacheConfig.adapterCacheSize());
        this.queryResultCache = new ConcurrentLRUCache<>(cacheConfig.queryResultCacheSize());
        this.entityPrefix    = repositoryModel.entitySimpleName() + ":";
        this.parallelExecutor = Executors.newFixedThreadPool(
            prefetchThreadPoolSize,
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "RelationshipHandler-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            }
        );
    }

    /**
     * Enable or disable parallel prefetching.
     * When enabled, independent relationship types (OneToMany, OneToOne, ManyToOne)
     * are loaded in parallel during prefetch operations.
     *
     * @param enabled true to enable parallel prefetching, false to disable (default: false)
     */
    public void setParallelPrefetchEnabled(boolean enabled) {
        this.parallelPrefetchEnabled = enabled;
        if (enabled) {
            Logging.deepInfo(() -> "Parallel prefetch enabled for " + entityPrefix);
        }
    }

    public boolean isParallelPrefetchEnabled() {
        return parallelPrefetchEnabled;
    }

    /**
     * Set the thread pool size for parallel operations.
     * Only takes effect when parallel prefetch is enabled.
     *
     * @param size number of threads (default: number of processors)
     */
    public void setPrefetchThreadPoolSize(int size) {
        if (size < 1) throw new IllegalArgumentException("Thread pool size must be at least 1");
        this.prefetchThreadPoolSize = size;
    }

    /**
     * Enable or disable automatic cache warming during prefetch operations.
     * When enabled, prefetch() will automatically call warmCache() with the collected IDs
     * before loading entity objects, potentially reducing duplicate queries.
     *
     * @param enabled true to enable auto-warm cache, false to disable (default: false)
     */
    public void setAutoWarmCache(boolean enabled) {
        this.autoWarmCache = enabled;
        if (enabled) {
            Logging.deepInfo(() -> "Auto-warm cache enabled for " + entityPrefix);
        }
    }

    public boolean isAutoWarmCache() {
        return autoWarmCache;
    }

    /**
     * Enable or disable automatic deep prefetching when loading collections.
     * When enabled, loading a OneToMany relationship will automatically
     * trigger deep prefetch on the loaded entities up to the configured depth.
     *
     * @param enabled true to enable auto-deep prefetch, false to disable (default: false)
     */
    public void setAutoDeepPrefetch(boolean enabled) {
        this.autoDeepPrefetch = enabled;
        if (enabled) {
            Logging.deepInfo(() -> "Auto-deep prefetch enabled for " + entityPrefix +
                " with depth " + autoDeepPrefetchDepth);
        }
    }

    public boolean isAutoDeepPrefetch() {
        return autoDeepPrefetch;
    }

    /**
     * Set the depth for automatic deep prefetching.
     * Only takes effect when auto-deep prefetch is enabled.
     *
     * @param depth maximum relationship depth to prefetch (default: 2)
     */
    public void setAutoDeepPrefetchDepth(int depth) {
        if (depth < 1) throw new IllegalArgumentException("Depth must be at least 1");
        this.autoDeepPrefetchDepth = depth;
    }

    public int getAutoDeepPrefetchDepth() {
        return autoDeepPrefetchDepth;
    }

    // ==================== Global Cache Configuration ====================

    /**
     * Globally set the size of the static name cache shared across all handlers.
     * This cache stores column name mappings.
     * <p><strong>Warning:</strong> Calling this will clear the existing cache.
     *
     * @param size maximum number of entries (default: 1,000)
     */
    public static void setNameCacheSize(int size) {
        if (size < 1) throw new IllegalArgumentException("Cache size must be at least 1");
        nameCacheSize = size;
        nameCache = new ConcurrentLRUCache<>(size);
    }

    public static int getNameCacheSize() {
        return nameCacheSize;
    }

    /**
     * Globally set the initial capacity for L1 thread-local caches.
     * <p><strong>Note:</strong> This only affects new thread-local caches created after this call.
     *
     * @param capacity initial hash map capacity (default: 64)
     */
    public static void setL1CacheInitialCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be at least 1");
        l1CacheInitialCapacity = capacity;
    }

    public static int getL1CacheInitialCapacity() {
        return l1CacheInitialCapacity;
    }

    private static ReadPolicy policyFor(@NotNull FieldModel<?> field) {
        return switch (field.consistency()) {
            case STRONG   -> ReadPolicy.STRONG_READ_POLICY;
            case EVENTUAL -> ReadPolicy.EVENTUAL_READ_POLICY;
            case NONE     -> ReadPolicy.NO_READ_POLICY;
        };
    }

    @Override
    public @Nullable Object handleManyToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        ReadPolicy policy   = policyFor(field);
        String     cacheKey = buildCacheKey(field.name(), primaryKeyValue);

        Object cached = getCached(cacheKey, policy);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

        RepositoryModel<?, ?> parentInfo = GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null) throw new IllegalStateException("Unknown repository for type " + field.type());

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, parentInfo);
        if (adapter == null) throw new IllegalStateException("Missing adapter for " + parentInfo.getEntityClass());

        SelectQuery query  = createQuery(primaryKeyValue, repositoryModel.getPrimaryKey().columnName(), repositoryModel);
        List<Object> result = adapter.find(query, policy);
        Object value = result.isEmpty() ? null : result.getFirst();

        if (policy.allowStale()) putCached(cacheKey, value == null ? NULL_MARKER : value);
        if (autoDeepPrefetch && value != null) autoDeepPrefetchRelated(List.of(value), parentInfo, 1);

        return value;
    }

    public SelectQuery createQuery(Object primaryKeyValue, String name, RepositoryModel<?, ?> model) {
        return Query.select()
            .where(name).eq(primaryKeyValue)
            .limit(1)
            .build();
    }

    private static final ThreadLocal<Set<String>> IN_PROGRESS =
        ThreadLocal.withInitial(() -> new HashSet<>(8));

    /**
     * Called when the CURRENT entity owns the FK column (e.g. factions.warp = warpId).
     * fkValue is the raw FK value already read from the result set.
     * Queries: SELECT * FROM warps WHERE id = ?
     */
    @Override
    public @Nullable Object handleOneToOneRelationshipOwning(Object fkValue, @NotNull FieldModel<T> field) {
        if (fkValue == null) return null;

        ReadPolicy policy   = policyFor(field);
        String     cacheKey = buildCacheKey(field.name() + "#fk", fkValue);

        Object cached = getCached(cacheKey, policy);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        Set<String> inProgress = IN_PROGRESS.get();
        if (!inProgress.add(cacheKey)) {
            putCached(cacheKey, NULL_MARKER);
            return null;
        }

        try {
            cacheMisses.incrementAndGet();
            incrementQueryCount(field.name());

            RepositoryModel<?, ?> targetInfo = GeneratedMetadata.getByEntityClass(field.type());
            if (targetInfo == null) throw new IllegalStateException("Unknown repository for type " + field.type());

            RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, targetInfo);
            if (adapter == null) { putCached(cacheKey, NULL_MARKER); return null; }

            SelectQuery  query   = createQuery(fkValue, targetInfo.getPrimaryKey().columnName(), targetInfo);
            List<Object> results = adapter.find(query, policy);
            Object       result  = results.isEmpty() ? null : results.getFirst();

            if (policy.allowStale()) putCached(cacheKey, result == null ? NULL_MARKER : result);
            if (autoDeepPrefetch && result != null) autoDeepPrefetchRelated(List.of(result), targetInfo, 1);

            return result;
        } finally {
            inProgress.remove(cacheKey);
        }
    }

    /**
     * Called when the TARGET entity owns the FK (inverse side).
     * Queries: SELECT * FROM warps WHERE faction_fk_col = parentId
     */
    @Override
    public @Nullable Object handleOneToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        ReadPolicy policy   = policyFor(field);
        String     cacheKey = buildCacheKey(field.name(), primaryKeyValue);

        Object cached = getCached(cacheKey, policy);
        if (cached != null) return cached == NULL_MARKER ? null : cached;

        Set<String> inProgress = IN_PROGRESS.get();
        if (!inProgress.add(cacheKey)) {
            putCached(cacheKey, NULL_MARKER);
            return null;
        }

        try {
            cacheMisses.incrementAndGet();
            incrementQueryCount(field.name());

            RepositoryModel<?, ?> targetInfo = GeneratedMetadata.getByEntityClass(field.type());
            if (targetInfo == null) throw new IllegalStateException("Unknown repository for type " + field.type());

            FieldModel<?> backRef = findOneToOneBackReference(targetInfo, repositoryModel.getEntityClass());
            if (backRef == null) { putCached(cacheKey, NULL_MARKER); return null; }

            RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, targetInfo);
            if (adapter == null) { putCached(cacheKey, NULL_MARKER); return null; }

            SelectQuery  query   = createBackRefQuery(primaryKeyValue, backRef);
            List<Object> results = adapter.find(query, policy);
            Object       result  = (results == null || results.isEmpty()) ? null : results.getFirst();

            if (policy.allowStale()) putCached(cacheKey, result == null ? NULL_MARKER : result);
            if (autoDeepPrefetch && result != null) autoDeepPrefetchRelated(List.of(result), targetInfo, 1);

            return result;
        } finally {
            inProgress.remove(cacheKey);
        }
    }

    public SelectQuery createBackRefQuery(ID primaryKeyValue, FieldModel<?> backRef) {
        return Query.select()
            .where(backRef.columnName())
            .eq(primaryKeyValue)
            .limit(1)
            .build();
    }

    @Override
    public List<Object> handleOneToManyRelationship(ID primaryKeyValue, FieldModel<T> field) {
        ReadPolicy policy   = policyFor(field);
        String     cacheKey = buildCacheKey(field.name(), primaryKeyValue);

        Object cached = getCached(cacheKey, policy);
        if (cached != null) return (List<Object>) cached;

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

        Class<?> targetType = field.elementType();
        if (targetType == null) throw new IllegalStateException("OneToMany field must have elementType: " + field.name());

        RepositoryModel<?, ?> relatedRepoInfo = GeneratedMetadata.getByEntityClass(targetType);
        if (relatedRepoInfo == null) throw new IllegalStateException("Unknown repository for type " + targetType);

        String relationName = findManyToOneFieldName(relatedRepoInfo, repositoryModel.getEntityClass());
        if (relationName == null) {
            throw new IllegalStateException(
                "No ManyToOne back-reference found in " + targetType.getSimpleName() +
                    " pointing to " + repositoryModel.getEntityClass().getSimpleName()
            );
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, relatedRepoInfo);
        if (adapter == null) throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getEntityClass());

        if (!field.lazy()) {
            List<Object> results = loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey, policy);
            if (autoDeepPrefetch && !results.isEmpty()) autoDeepPrefetchRelated(results, relatedRepoInfo, 1);
            return results;
        }

        return new LazyArrayList<>(() -> {
            List<Object> results = loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey, policy);
            if (autoDeepPrefetch && !results.isEmpty()) autoDeepPrefetchRelated(results, relatedRepoInfo, 1);
            return results;
        });
    }

    private List<Object> loadOneToManyResults(
        ID primaryKeyValue,
        RepositoryAdapter<Object, Object, ?> adapter,
        String relationName,
        String cacheKey,
        ReadPolicy policy
    ) {
        String queryKey = relationName + "=" + primaryKeyValue;

        if (policy.allowStale()) {
            List<Object> cachedQuery = queryResultCache.get(queryKey);
            if (cachedQuery != null) {
                putCached(cacheKey, cachedQuery);
                return cachedQuery;
            }
        }

        List<Object> result = adapter.find(
            createQuery(primaryKeyValue, relationName, repositoryModel),
            policy
        );

        List<Object> immutable = result == null ? Collections.emptyList() : List.copyOf(result);

        if (policy.allowStale()) {
            queryResultCache.put(queryKey, immutable);
            putCached(cacheKey, immutable);
        }

        return immutable;
    }

    private void autoDeepPrefetchRelated(List<Object> entities, RepositoryModel<?, ?> model, int currentDepth) {
        if (entities.isEmpty() || currentDepth >= autoDeepPrefetchDepth) return;

        RelationshipHandler<?, ?> handler = getRelatedHandler(model);
        if (!(handler instanceof AbstractRelationshipHandler<?, ?> abstractHandler)) return;

        List<? extends RelationshipModel<?, ?>> relationships = model.getRelationships();
        Set<String> relationshipFields = new HashSet<>(relationships.size());
        for (RelationshipModel<?, ?> field : relationships) {
            relationshipFields.add(field.getFieldModel().name());
        }

        if (relationshipFields.isEmpty()) return;

        Logging.deepInfo(() -> "Auto-deep prefetch at depth " + currentDepth +
            " for " + entities.size() + " " + model.entitySimpleName() +
            " entities, fields: " + relationshipFields);

        abstractHandler.prefetch(entities, relationshipFields);
    }

    @Nullable
    private static FieldModel<?> findOneToOneBackReference(
        @NotNull RepositoryModel<?, ?> targetInfo,
        @NotNull Class<?> sourceEntityType
    ) {
        for (FieldModel<?> field : targetInfo.getOneToOneCache().values()) {
            if (sourceEntityType.isAssignableFrom(field.type())) return field;
        }
        return null;
    }

    @Nullable
    private static String findManyToOneFieldName(
        @NotNull RepositoryModel<?, ?> targetInfo,
        @NotNull Class<?> parentType
    ) {
        String cacheKey = (targetInfo.tableName() + "#" + parentType.getName()).intern();

        return nameCache.computeIfAbsent(cacheKey, k -> {
            for (FieldModel<?> field : targetInfo.getManyToOneCache().values()) {
                if (field.type() == parentType) return field.columnName();
            }
            Logging.deepInfo(() ->
                "ManyToOne field for parent type " + parentType.getName() +
                    " not found in " + targetInfo.tableName()
            );
            return null;
        });
    }

    @SuppressWarnings("RedundantCast")
    @Nullable
    private RepositoryAdapter<Object, Object, ?> resolveAdapterCached(
        @NotNull FieldModel<?> field,
        @NotNull RepositoryModel<?, ?> targetInfo
    ) {
        String cacheKey = field.name() + ":" + targetInfo.getEntityClass().getName();

        return adapterCache.computeIfAbsent(cacheKey, k -> {
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
        });
    }

    private void batchLoadOneToOne(FieldModel<T> field, List<ID> parentIds) {
        RepositoryModel<?, ?> target = GeneratedMetadata.getByEntityClass(field.type());
        if (target == null) throw new IllegalStateException("Unknown repository for type " + field.type());

        FieldModel<Object> backRef = (FieldModel<Object>) findOneToOneBackReference(target, repositoryModel.getEntityClass());
        if (backRef == null) { Logging.error("No OneToOne back-reference for field: " + field.name()); return; }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, target);
        if (adapter == null) { Logging.error("No adapter found for type: " + field.type()); return; }

        SelectQuery  query   = Query.select().where(backRef.columnName()).in(parentIds).build();
        List<Object> results = adapter.find(query, policyFor(field));
        Map<ID, Object> mapped = new HashMap<>(results.size());

        for (Object obj : results) {
            ID parentId = (ID) backRef.getValue(obj);
            if (mapped.put(parentId, obj) != null) {
                throw new IllegalStateException("Multiple one-to-one results for field " + field.name());
            }
        }

        for (ID id : parentIds) {
            putCached(buildCacheKey(field.name(), id), mapped.getOrDefault(id, NULL_MARKER));
        }

        incrementQueryCount(field.name());
    }

    private void batchLoadOneToMany(FieldModel<T> field, List<ID> parentIds) {
        Class<?> targetType = field.elementType();
        if (targetType == null) throw new IllegalStateException("OneToMany field must have elementType: " + field.name());

        RepositoryModel<?, ?> related = GeneratedMetadata.getByEntityClass(targetType);
        if (related == null) throw new IllegalStateException("Unknown repository for type " + targetType);

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, related);
        if (adapter == null) throw new IllegalStateException("No adapter found for type: " + targetType);

        String relationName = findManyToOneFieldName(related, repositoryModel.getEntityClass());
        if (relationName == null) {
            throw new IllegalStateException("No ManyToOne back-reference found for OneToMany field: " + field.name());
        }

        List<Object> results = adapter.find(
            Query.select().where(relationName).in(parentIds).build(),
            policyFor(field)
        );

        Map<ID, List<Object>> grouped   = new HashMap<>(parentIds.size());
        FieldModel<Object>    backRefField = (FieldModel<Object>) related.fieldByName(relationName);

        for (Object child : results) {
            ID parentId = (ID) backRefField.getValue(child);
            //noinspection ObjectAllocationInLoop
            grouped.computeIfAbsent(parentId, k -> new ArrayList<>(16)).add(child);
        }

        for (ID id : parentIds) {
            putCached(buildCacheKey(field.name(), id), List.copyOf(grouped.getOrDefault(id, List.of())));
        }

        incrementQueryCount(field.name());
    }

    private void batchLoadManyToOne(FieldModel<T> field, List<ID> childIds) {
        RepositoryModel<Object, ?> parentInfo =
            (RepositoryModel<Object, ?>) GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null) { Logging.error("Unknown repository for type: " + field.type()); return; }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, parentInfo);
        if (adapter == null) { Logging.error("No adapter found for type: " + field.type()); return; }

        List<Object> parents = adapter.find(
            Query.select().where(parentInfo.getPrimaryKey().columnName()).in(childIds).build(),
            policyFor(field)
        );

        FieldModel<Object> pkField    = parentInfo.getPrimaryKey();
        Map<ID, Object>    parentMap  = new HashMap<>(parents.size());
        for (Object parent : parents) {
            ID parentId = (ID) pkField.getValue(parent);
            parentMap.put(parentId, parent);
        }

        for (ID childId : childIds) {
            Object parent = parentMap.get(childId);
            putCached(buildCacheKey(field.name(), childId), parent == null ? NULL_MARKER : parent);
        }

        incrementQueryCount(field.name());
    }

    @SuppressWarnings("ObjectAllocationInLoop")
    @Override
    public void prefetch(Collection<Object> parents, Set<String> fields) {
        List<ID> parentIds = new ArrayList<>(parents.size());
        for (Object parent : parents) {
            parentIds.add(repositoryModel.getPrimaryKeyValue((T) parent));
        }

        if (autoWarmCache && !parentIds.isEmpty()) {
            Logging.deepInfo(() -> "Auto-warming cache for " + parentIds.size() + " entities");
            warmCache(parentIds, fields);
            return;
        }

        Map<FieldModel<T>, List<ID>> oneToMany  = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> oneToOne   = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> manyToOne  = new HashMap<>(fields.size());

        // Use pre-built field indexes for O(1) relationship field lookup (compile-time generated)
        Map<RelationshipKind, List<FieldModel<T>>> fieldIndexes = repositoryModel.getFieldIndexes();
        if (!fieldIndexes.isEmpty()) {
            // Fast path: filter pre-grouped relationship fields by requested field names
            for (Object parent : parents) {
                ID id = repositoryModel.getPrimaryKeyValue((T) parent);

                List<FieldModel<T>> oneToManyFields = fieldIndexes.get(RelationshipKind.ONE_TO_MANY);
                if (oneToManyFields != null) {
                    for (FieldModel<T> field : oneToManyFields) {
                        if (fields.contains(field.name())) {
                            oneToMany.computeIfAbsent(field, f -> new ArrayList<>(16)).add(id);
                        }
                    }
                }

                List<FieldModel<T>> oneToOneFields = fieldIndexes.get(RelationshipKind.ONE_TO_ONE);
                if (oneToOneFields != null) {
                    for (FieldModel<T> field : oneToOneFields) {
                        if (fields.contains(field.name())) {
                            oneToOne.computeIfAbsent(field, f -> new ArrayList<>(16)).add(id);
                        }
                    }
                }

                List<FieldModel<T>> manyToOneFields = fieldIndexes.get(RelationshipKind.MANY_TO_ONE);
                if (manyToOneFields != null) {
                    for (FieldModel<T> field : manyToOneFields) {
                        if (fields.contains(field.name())) {
                            manyToOne.computeIfAbsent(field, f -> new ArrayList<>(16)).add(id);
                        }
                    }
                }
            }
        } else {
            // Fallback: original behavior for repositories without compile-time indexes
            for (Object parent : parents) {
                ID id = repositoryModel.getPrimaryKeyValue((T) parent);

                for (String fieldName : fields) {
                    FieldModel<T> field = repositoryModel.fieldByName(fieldName);
                    if (field == null || !field.relationship()) continue;

                    switch (field.relationshipKind()) {
                        case ONE_TO_MANY  -> oneToMany .computeIfAbsent(field, f -> new ArrayList<>(16)).add(id);
                        case ONE_TO_ONE   -> oneToOne  .computeIfAbsent(field, f -> new ArrayList<>(16)).add(id);
                        case MANY_TO_ONE  -> manyToOne .computeIfAbsent(field, f -> new ArrayList<>(16)).add(id);
                    }
                }
            }
        }

        prefetchAndBatchOptionallyParallel(oneToMany, oneToOne, manyToOne);
    }

    /**
     * Deep prefetch with recursive relationship loading using dot notation.
     *
     * @param parents Parent entities to prefetch for
     * @param dotNotationFields Field paths in dot notation (e.g., "faction.warp.location")
     */
    @SuppressWarnings("ObjectAllocationInLoop")
    public void prefetchDeep(Collection<Object> parents, String... dotNotationFields) {
        if (dotNotationFields.length == 0) return;

        Map<String, Set<String>> fieldPaths = new HashMap<>(dotNotationFields.length);

        for (String dotPath : dotNotationFields) {
            String[] parts     = PATTERN.split(dotPath, 2);
            String   rootField = parts[0];

            if (parts.length == 1) {
                fieldPaths.computeIfAbsent(rootField, k -> new HashSet<>(32));
            } else {
                fieldPaths.computeIfAbsent(rootField, k -> new HashSet<>(32)).add(parts[1]);
            }
        }

        prefetchDeep(parents, fieldPaths);
    }

    /**
     * Deep prefetch with recursive relationship loading.
     *
     * @param parents    Parent entities to prefetch for
     * @param fieldPaths Map of field names to their nested fields
     */
    public void prefetchDeep(Collection<Object> parents, Map<String, Set<String>> fieldPaths) {
        if (fieldPaths.isEmpty()) return;

        prefetch(parents, fieldPaths.keySet());

        Map<String, Set<String>> nestedPaths    = new HashMap<>(32);
        List<Object>             relatedEntities = new ArrayList<>(32);

        for (Map.Entry<String, Set<String>> entry : fieldPaths.entrySet()) {
            Set<String> nestedFields = entry.getValue();
            if (nestedFields.isEmpty()) continue;

            FieldModel<T> field = repositoryModel.fieldByName(entry.getKey());
            if (field == null || !field.relationship()) continue;

            for (Object parent : parents) {
                ID     parentId = repositoryModel.getPrimaryKeyValue((T) parent);
                String cacheKey = buildCacheKey(field.name(), parentId);
                Object related  = relationshipCache.get(cacheKey);

                if (related != null && related != NULL_MARKER) {
                    if (related instanceof Collection) {
                        relatedEntities.addAll((Collection<?>) related);
                    } else {
                        relatedEntities.add(related);
                    }
                }
            }

            if (!relatedEntities.isEmpty()) {
                Class<?> relatedType = field.relationshipKind() == RelationshipKind.ONE_TO_MANY
                    ? field.elementType()
                    : field.type();

                if (relatedType == null) continue;

                RepositoryModel<?, ?> relatedModel = GeneratedMetadata.getByEntityClass(relatedType);
                if (relatedModel != null) {
                    RelationshipHandler<?, ?> relatedHandler = getRelatedHandler(relatedModel);

                    if (relatedHandler instanceof AbstractRelationshipHandler<?, ?> abstractHandler) {
                        for (String nestedField : nestedFields) {
                            nestedPaths.put(nestedField, Set.of());
                        }
                        abstractHandler.prefetchDeep(relatedEntities, nestedPaths);
                    } else if (relatedHandler != null) {
                        relatedHandler.prefetch(relatedEntities, nestedFields);
                    }
                }

                nestedFields.clear();
                relatedEntities.clear();
            }
        }
    }

    protected static RelationshipHandler<?, ?> getRelatedHandler(RepositoryModel<?, ?> relatedModel) {
        RepositoryAdapter<?, ?, ?> adapter = RepositoryRegistry.get(relatedModel.getEntityClass());
        Objects.requireNonNull(adapter);
        return adapter.getRelationshipHandler();
    }

    /**
     * Warm cache for anticipated access patterns using only IDs.
     *
     * @param ids               Entity IDs to warm cache for
     * @param anticipatedFields Field names that will likely be accessed
     */
    public void warmCache(List<ID> ids, Set<String> anticipatedFields) {
        if (ids.isEmpty() || anticipatedFields.isEmpty()) return;

        Logging.deepInfo(() -> "Warming cache for " + ids.size() +
            " entities, " + anticipatedFields.size() + " fields");

        Map<FieldModel<T>, List<ID>> oneToManyFields  = new HashMap<>(anticipatedFields.size());
        Map<FieldModel<T>, List<ID>> oneToOneFields   = new HashMap<>(anticipatedFields.size());
        Map<FieldModel<T>, List<ID>> manyToOneFields  = new HashMap<>(anticipatedFields.size());

        for (String fieldName : anticipatedFields) {
            FieldModel<T> field = repositoryModel.fieldByName(fieldName);
            if (field == null || !field.relationship()) continue;

            switch (field.relationshipKind()) {
                case ONE_TO_MANY  -> oneToManyFields .put(field, ids);
                case ONE_TO_ONE   -> oneToOneFields  .put(field, ids);
                case MANY_TO_ONE  -> manyToOneFields .put(field, ids);
            }
        }

        prefetchAndBatchOptionallyParallel(oneToManyFields, oneToOneFields, manyToOneFields);
    }

    private void prefetchAndBatchOptionallyParallel(
        Map<FieldModel<T>, List<ID>> oneToManyFields,
        Map<FieldModel<T>, List<ID>> oneToOneFields,
        Map<FieldModel<T>, List<ID>> manyToOneFields
    ) {
        if (parallelPrefetchEnabled) {
            try {
                CompletableFuture<Void>[] futures = new CompletableFuture[3];
                futures[0] = CompletableFuture.runAsync(
                    () -> oneToManyFields .forEach(this::batchLoadOneToMany),  parallelExecutor);
                futures[1] = CompletableFuture.runAsync(
                    () -> oneToOneFields  .forEach(this::batchLoadOneToOne),   parallelExecutor);
                futures[2] = CompletableFuture.runAsync(
                    () -> manyToOneFields .forEach(this::batchLoadManyToOne),  parallelExecutor);

                CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel prefetch interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException("Parallel prefetch failed", e);
            }
        } else {
            oneToManyFields .forEach(this::batchLoadOneToMany);
            oneToOneFields  .forEach(this::batchLoadOneToOne);
            manyToOneFields .forEach(this::batchLoadManyToOne);
        }
    }

    public void shutdown() {
        clearThreadLocalCache();
        parallelExecutor.shutdown();
        try {
            if (!parallelExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                parallelExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            parallelExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @NotNull
    private String buildCacheKey(@NotNull String fieldName, Object id) {
        if (id instanceof Integer || id instanceof Long) {
            String key      = entityPrefix + id + ":" + fieldName;
            int    hash     = key.hashCode();
            int    poolIndex = (hash & Integer.MAX_VALUE) % cacheKeyPoolSizeInstance;

            String pooled = cacheKeyPool[poolIndex];
            if (pooled == null || !pooled.equals(key)) {
                cacheKeyPool[poolIndex] = key.intern();
                return cacheKeyPool[poolIndex];
            }
            return pooled;
        }
        return entityPrefix + id + ":" + fieldName;
    }

    private static Map<String, Object> getOrCreateL1Cache() {
        WeakReference<Map<String, Object>> ref = l1Cache.get();
        Map<String, Object> map = ref.get();
        if (map == null) {
            map = new HashMap<>(64);
            l1Cache.set(new WeakReference<>(map));
        }
        return map;
    }

    /**
     * Get cached value using tiered caching strategy.
     * Checks L1 (thread-local) cache first, then L2 (shared) cache.
     * Increments the appropriate hit counter on success.
     */
    @Nullable
    private Object getCached(String cacheKey, ReadPolicy policy) {
        if (!policy.allowStale()) return null;

        Map<String, Object> l1 = getOrCreateL1Cache();
        Object l1Result = l1.get(cacheKey);
        if (l1Result != null) {
            l1CacheHits.incrementAndGet();
            return l1Result;
        }

        Object l2Result = relationshipCache.get(cacheKey);
        if (l2Result != null) {
            l2CacheHits.incrementAndGet();
            l1.put(cacheKey, l2Result);
            return l2Result;
        }

        return null;
    }

    /** Put value in both L1 and L2 cache. */
    private void putCached(String cacheKey, Object value) {
        relationshipCache.put(cacheKey, value);
        getOrCreateL1Cache().put(cacheKey, value);
    }

    /**
     * Clear thread-local L1 cache.
     * Call periodically in long-running threads to prevent memory buildup.
     */
    public static void clearThreadLocalCache() {
        l1Cache.remove();
    }

    @Override
    public void invalidateRelationshipsForId(@NotNull ID id) {
        String prefix = entityPrefix + id + ":";
        relationshipCache.keySet().removeIf(key -> key.startsWith(prefix));

        Map<String, Object> l1 = getOrCreateL1Cache();
        l1.keySet().removeIf(key -> key.startsWith(prefix));

        queryResultCache.keySet().removeIf(key -> key.contains("=" + id));
    }

    @Override
    public void clear() {
        relationshipCache.clear();
        queryResultCache.clear();
        clearThreadLocalCache();
        adapterCache.clear();
        Arrays.fill(cacheKeyPool, null);
    }

    /** Clear all caches including static shared caches. Use with caution. */
    public void clearAll() {
        clear();
        nameCache.clear();
    }

    private void incrementQueryCount(String fieldName) {
        queryCountByField.computeIfAbsent(fieldName, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Overall cache hit rate across both tiers: (l1Hits + l2Hits) / (l1Hits + l2Hits + misses).
     * Returns 0.0 when no lookups have occurred yet.
     */
    public double getCacheHitRate() {
        long l1Hits  = l1CacheHits.get();
        long l2Hits  = l2CacheHits.get();
        long misses  = cacheMisses.get();
        long total   = l1Hits + l2Hits + misses;
        return total == 0 ? 0.0 : (double) (l1Hits + l2Hits) / total;
    }

    /**
     * Get the size of a OneToMany relationship without loading the full collection.
     *
     * @param primaryKeyValue The parent entity ID
     * @param field           The OneToMany field
     * @return The number of related entities
     */
    public int getRelationshipSize(ID primaryKeyValue, FieldModel<T> field) {
        if (field.relationshipKind() != RelationshipKind.ONE_TO_MANY) {
            throw new IllegalArgumentException("Only ONE_TO_MANY supports size queries");
        }

        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);
        Object cached   = relationshipCache.get(cacheKey);
        if (cached instanceof List<?> list) return list.size();

        Class<?>              targetType      = field.elementType();
        RepositoryModel<?, ?> relatedRepoInfo = GeneratedMetadata.getByEntityClass(targetType);
        Objects.requireNonNull(relatedRepoInfo);
        String relationName = findManyToOneFieldName(relatedRepoInfo, repositoryModel.getEntityClass());

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, relatedRepoInfo);
        Objects.requireNonNull(adapter);

        return (int) adapter.count(
            Query.select().where(relationName).eq(primaryKeyValue).build(),
            policyFor(field)
        );
    }

    /**
     * Snapshot of all performance counters at a point in time.
     *
     * @return an immutable {@link RelationshipMetrics} record
     */
    public RelationshipMetrics getMetrics() {
        long l1Hits = l1CacheHits.get();
        long l2Hits = l2CacheHits.get();
        long misses = cacheMisses.get();

        Map<String, Long> queryCountsCopy = new HashMap<>(queryCountByField.size());
        queryCountByField.forEach((field, count) -> queryCountsCopy.put(field, count.get()));

        Map<String, Object> l1 = getOrCreateL1Cache();

        return new RelationshipMetrics(
            misses,
            l1Hits,
            l2Hits,
            l1Hits + l2Hits,
            relationshipCache.size(),
            l1.size(),
            queryResultCache.size(),
            adapterCache.size(),
            queryCountsCopy,
            parallelPrefetchEnabled,
            autoWarmCache,
            autoDeepPrefetch,
            autoDeepPrefetchDepth
        );
    }

    /** Reset all metrics counters. Does not affect cached data. */
    public void resetMetrics() {
        cacheMisses.set(0);
        l1CacheHits.set(0);
        l2CacheHits.set(0);
        queryCountByField.clear();
    }

    /**
     * Immutable snapshot of relationship handler performance counters.
     *
     * <h3>Cache hit rates</h3>
     * <ul>
     *   <li>{@link #overallHitRate()} — fraction of all lookups satisfied by either cache tier.</li>
     *   <li>{@link #l1HitRate()} — fraction of <em>cache hits</em> that were served from L1
     *       (thread-local). High values indicate good thread locality.</li>
     *   <li>{@link #l2HitRate()} — fraction of <em>cache hits</em> that fell through to L2
     *       (shared LRU). High values with low L1 rate may indicate thread churn.</li>
     * </ul>
     *
     * @param cacheMisses            Lookups that found nothing in either cache tier.
     * @param l1CacheHits            Lookups satisfied by the thread-local L1 cache.
     * @param l2CacheHits            Lookups satisfied by the shared L2 LRU cache.
     * @param totalCacheHits         {@code l1CacheHits + l2CacheHits} — precomputed for convenience.
     * @param l2CacheSize            Current number of entries in the shared L2 cache.
     * @param l1CacheSize            Current number of entries in the calling thread's L1 cache.
     * @param queryResultCacheSize   Current number of cached query result lists.
     * @param adapterCacheSize       Current number of cached adapter lookups.
     * @param queryCountsByField     Total queries issued per relationship field name.
     * @param parallelPrefetchEnabled Whether parallel prefetch is active.
     * @param autoWarmCache          Whether automatic cache warming is active.
     * @param autoDeepPrefetch       Whether automatic deep prefetch is active.
     * @param autoDeepPrefetchDepth  Configured maximum depth for automatic deep prefetch.
     */
    public record RelationshipMetrics(
        long cacheMisses,
        long l1CacheHits,
        long l2CacheHits,
        long totalCacheHits,
        int  l2CacheSize,
        int  l1CacheSize,
        int  queryResultCacheSize,
        int  adapterCacheSize,
        Map<String, Long> queryCountsByField,
        boolean parallelPrefetchEnabled,
        boolean autoWarmCache,
        boolean autoDeepPrefetch,
        int     autoDeepPrefetchDepth
    ) {
        /**
         * Fraction of all cache lookups satisfied by either tier.
         * {@code totalCacheHits / (totalCacheHits + cacheMisses)}.
         * Returns {@code 0.0} when no lookups have occurred.
         */
        public double overallHitRate() {
            long total = totalCacheHits + cacheMisses;
            return total == 0 ? 0.0 : (double) totalCacheHits / total;
        }

        /**
         * Fraction of cache hits served by L1 (thread-local).
         * {@code l1CacheHits / totalCacheHits}.
         * Returns {@code 0.0} when there are no cache hits at all.
         *
         * <p>High values (&gt; 0.8) indicate good thread locality — most
         * repeated accesses stay within the same thread's working set.
         */
        public double l1HitRate() {
            return totalCacheHits == 0 ? 0.0 : (double) l1CacheHits / totalCacheHits;
        }

        /**
         * Fraction of cache hits served by L2 (shared LRU).
         * {@code l2CacheHits / totalCacheHits}.
         * Returns {@code 0.0} when there are no cache hits at all.
         *
         * <p>High values with low {@link #l1HitRate()} suggest significant
         * cross-thread access patterns; consider reviewing thread affinity
         * or increasing L1 warm-up strategies.
         */
        public double l2HitRate() {
            return totalCacheHits == 0 ? 0.0 : (double) l2CacheHits / totalCacheHits;
        }
    }
}