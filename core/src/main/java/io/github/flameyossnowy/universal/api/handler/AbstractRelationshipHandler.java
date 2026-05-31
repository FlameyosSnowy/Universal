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
 */
@SuppressWarnings({ "unchecked", "unused", "DuplicatedCode" })
public abstract class AbstractRelationshipHandler<T, ID> implements RelationshipHandler<T, ID> {

    /**
     * Configuration for relationship handler caches and batching.
     * Use {@link #builder()} to create instances with custom settings.
     */
    public record CacheConfiguration(
            int relationshipCacheSize,
            int adapterCacheSize,
            int queryResultCacheSize,
            int cacheKeyPoolSize,
            int inListChunkSize,
            int l1MaxSize
    ) {
        public static final int DEFAULT_RELATIONSHIP_CACHE_SIZE = 10_000;
        public static final int DEFAULT_ADAPTER_CACHE_SIZE      = 1_000;
        public static final int DEFAULT_QUERY_RESULT_CACHE_SIZE = 5_000;
        public static final int DEFAULT_CACHE_KEY_POOL_SIZE     = 1_024;
        public static final int DEFAULT_IN_LIST_CHUNK_SIZE      = 1_000;
        public static final int DEFAULT_L1_MAX_SIZE             = 512;

        public CacheConfiguration {
            if (relationshipCacheSize < 1) throw new IllegalArgumentException("relationshipCacheSize must be >= 1");
            if (adapterCacheSize      < 1) throw new IllegalArgumentException("adapterCacheSize must be >= 1");
            if (queryResultCacheSize  < 1) throw new IllegalArgumentException("queryResultCacheSize must be >= 1");
            if (cacheKeyPoolSize      < 1) throw new IllegalArgumentException("cacheKeyPoolSize must be >= 1");
            if (inListChunkSize       < 1) throw new IllegalArgumentException("inListChunkSize must be >= 1");
            if (l1MaxSize             < 1) throw new IllegalArgumentException("l1MaxSize must be >= 1");
        }

        /** Default configuration. */
        public CacheConfiguration() {
            this(
                    DEFAULT_RELATIONSHIP_CACHE_SIZE,
                    DEFAULT_ADAPTER_CACHE_SIZE,
                    DEFAULT_QUERY_RESULT_CACHE_SIZE,
                    DEFAULT_CACHE_KEY_POOL_SIZE,
                    DEFAULT_IN_LIST_CHUNK_SIZE,
                    DEFAULT_L1_MAX_SIZE
            );
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int relationshipCacheSize = DEFAULT_RELATIONSHIP_CACHE_SIZE;
            private int adapterCacheSize      = DEFAULT_ADAPTER_CACHE_SIZE;
            private int queryResultCacheSize  = DEFAULT_QUERY_RESULT_CACHE_SIZE;
            private int cacheKeyPoolSize      = DEFAULT_CACHE_KEY_POOL_SIZE;
            private int inListChunkSize       = DEFAULT_IN_LIST_CHUNK_SIZE;
            private int l1MaxSize             = DEFAULT_L1_MAX_SIZE;

            private Builder() {}

            public Builder relationshipCacheSize(int v) { this.relationshipCacheSize = v; return this; }
            public Builder adapterCacheSize(int v)      { this.adapterCacheSize      = v; return this; }
            public Builder queryResultCacheSize(int v)  { this.queryResultCacheSize  = v; return this; }
            public Builder cacheKeyPoolSize(int v)      { this.cacheKeyPoolSize      = v; return this; }
            public Builder inListChunkSize(int v)       { this.inListChunkSize       = v; return this; }
            public Builder l1MaxSize(int v)             { this.l1MaxSize             = v; return this; }

            public CacheConfiguration build() {
                return new CacheConfiguration(
                        relationshipCacheSize, adapterCacheSize, queryResultCacheSize,
                        cacheKeyPoolSize, inListChunkSize, l1MaxSize
                );
            }
        }
    }

    protected final RepositoryModel<T, ID>   repositoryModel;
    protected final Class<ID>                idClass;
    protected final TypeResolverRegistry     resolverRegistry;

    private final ExecutorService parallelExecutor;

    private static volatile int DEFAULT_L1_MAX_SIZE = CacheConfiguration.DEFAULT_L1_MAX_SIZE;

    /**
     * Thread-local L1 cache. Bounded to {@link #DEFAULT_L1_MAX_SIZE} entries so
     * long-lived thread-pool threads don't accumulate unbounded state.
     * WeakReference keeps the map itself collectable when the thread dies.
     */
    private static final ThreadLocal<WeakReference<Map<String, Object>>> l1Cache =
            ThreadLocal.withInitial(() -> new WeakReference<>(newBoundedL1Map()));

    private static Map<String, Object> newBoundedL1Map() {
        int cap = DEFAULT_L1_MAX_SIZE;

        return new LinkedHashMap<>(cap, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                return size() > cap;
            }
        };
    }

    private final Map<String, Object>                        relationshipCache;
    private final Map<String, List<Object>>                  queryResultCache;
    private final int                                        cacheKeyPoolSizeInstance;
    private final String[]                                   cacheKeyPool;

    /** Maximum IDs per IN-list chunk. Configurable per adapter instance. */
    private final int inListChunkSize;


    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong l1CacheHits = new AtomicLong();
    private final AtomicLong l2CacheHits = new AtomicLong();
    private final Map<String, AtomicLong> queryCountByField = new ConcurrentHashMap<>(32);

    /**
     * Stored in the cache to represent a known-null result.
     * Never escapes private helpers, all public/protected methods return actual
     * {@code null} rather than this sentinel.
     */
    private static final Object NULL_MARKER = new Object();

    private final String entityPrefix;

    private volatile boolean parallelPrefetchEnabled = false;
    private volatile int     prefetchThreadPoolSize  = Runtime.getRuntime().availableProcessors();
    private volatile boolean autoWarmCache           = false;
    private volatile boolean autoDeepPrefetch        = false;
    private volatile int     autoDeepPrefetchDepth   = 2;

    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

    protected AbstractRelationshipHandler(
            RepositoryModel<T, ID> repositoryModel,
            Class<ID> idClass,
            TypeResolverRegistry resolverRegistry
    ) {
        this(repositoryModel, idClass, resolverRegistry, new CacheConfiguration());
    }

    protected AbstractRelationshipHandler(
            RepositoryModel<T, ID> repositoryModel,
            Class<ID> idClass,
            TypeResolverRegistry resolverRegistry,
            CacheConfiguration cacheConfig
    ) {
        this.repositoryModel          = repositoryModel;
        this.idClass                  = idClass;
        this.resolverRegistry         = resolverRegistry;
        this.cacheKeyPoolSizeInstance = cacheConfig.cacheKeyPoolSize();
        this.cacheKeyPool             = new String[cacheKeyPoolSizeInstance];
        this.inListChunkSize          = cacheConfig.inListChunkSize();
        this.relationshipCache        = new ConcurrentLRUCache<>(cacheConfig.relationshipCacheSize());
        this.queryResultCache         = new ConcurrentLRUCache<>(cacheConfig.queryResultCacheSize());
        this.entityPrefix             = repositoryModel.entitySimpleName() + ":";
        this.parallelExecutor         = Executors.newFixedThreadPool(
                prefetchThreadPoolSize,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                        Thread t = new Thread(r, "RelationshipHandler-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                }
        );
    }

    public void setParallelPrefetchEnabled(boolean enabled) {
        this.parallelPrefetchEnabled = enabled;
        if (enabled) Logging.deepInfo(() -> "Parallel prefetch enabled for " + entityPrefix);
    }

    public boolean isParallelPrefetchEnabled() { return parallelPrefetchEnabled; }

    public void setPrefetchThreadPoolSize(int size) {
        if (size < 1) throw new IllegalArgumentException("Thread pool size must be at least 1");
        this.prefetchThreadPoolSize = size;
    }

    public void setAutoWarmCache(boolean enabled) {
        this.autoWarmCache = enabled;
        if (enabled) Logging.deepInfo(() -> "Auto-warm cache enabled for " + entityPrefix);
    }

    public boolean isAutoWarmCache() { return autoWarmCache; }

    public void setAutoDeepPrefetch(boolean enabled) {
        this.autoDeepPrefetch = enabled;
        if (enabled) Logging.deepInfo(() -> "Auto-deep prefetch enabled for " + entityPrefix
                + " with depth " + autoDeepPrefetchDepth);
    }

    public boolean isAutoDeepPrefetch() { return autoDeepPrefetch; }

    public void setAutoDeepPrefetchDepth(int depth) {
        if (depth < 1) throw new IllegalArgumentException("Depth must be at least 1");
        this.autoDeepPrefetchDepth = depth;
    }

    public int getAutoDeepPrefetchDepth() { return autoDeepPrefetchDepth; }

    /**
     * Globally adjust the maximum size of the per-thread L1 cache.
     * Only affects caches created after this call.
     */
    public static void setDefaultL1MaxSize(int size) {
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        DEFAULT_L1_MAX_SIZE = size;
    }

    public static int getDefaultL1MaxSize() { return DEFAULT_L1_MAX_SIZE; }

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

        Object cached = getCachedRaw(cacheKey, policy);
        if (cached != null) return unbox(cached);

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

        RepositoryModel<?, ?> parentInfo = GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null)
            throw new IllegalStateException("Unknown repository for type " + field.type());

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, parentInfo);
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + parentInfo.getEntityClass());

        SelectQuery  query  = createQuery(primaryKeyValue, repositoryModel.getPrimaryKey().columnName(), repositoryModel);
        List<Object> result = adapter.find(query, policy);
        Object       value  = result.isEmpty() ? null : result.getFirst();

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

    @Override
    public @Nullable Object handleOneToOneRelationshipOwning(Object fkValue, @NotNull FieldModel<T> field) {
        if (fkValue == null) return null;

        ReadPolicy policy   = policyFor(field);
        String     cacheKey = buildCacheKey(field.name() + "#fk", fkValue);

        Object cached = getCachedRaw(cacheKey, policy);
        if (cached != null) return unbox(cached);

        Set<String> inProgress = IN_PROGRESS.get();
        if (!inProgress.add(cacheKey)) {
            putCached(cacheKey, NULL_MARKER);
            return null;
        }

        try {
            cacheMisses.incrementAndGet();
            incrementQueryCount(field.name());

            RepositoryModel<?, ?> targetInfo = GeneratedMetadata.getByEntityClass(field.type());
            if (targetInfo == null)
                throw new IllegalStateException("Unknown repository for type " + field.type());

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

    @Override
    public @Nullable Object handleOneToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        ReadPolicy policy   = policyFor(field);
        String     cacheKey = buildCacheKey(field.name(), primaryKeyValue);

        Object cached = getCachedRaw(cacheKey, policy);
        if (cached != null) return unbox(cached);

        Set<String> inProgress = IN_PROGRESS.get();
        if (!inProgress.add(cacheKey)) {
            putCached(cacheKey, NULL_MARKER);
            return null;
        }

        try {
            cacheMisses.incrementAndGet();
            incrementQueryCount(field.name());

            RepositoryModel<?, ?> targetInfo = GeneratedMetadata.getByEntityClass(field.type());
            if (targetInfo == null)
                throw new IllegalStateException("Unknown repository for type " + field.type());

            FieldModel<?> backRef = targetInfo.getOneToOneBackReferences()
                    .get(repositoryModel.getEntityClass().getName());
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

        Object cached = getCachedRaw(cacheKey, policy);
        if (cached != null) {
            // Cached value is always a List for this relationship kind.
            // NULL_MARKER is never stored for one-to-many
            return (List<Object>) cached;
        }

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

        Class<?> targetType = field.elementType();
        if (targetType == null)
            throw new IllegalStateException("OneToMany field must have elementType: " + field.name());

        RepositoryModel<?, ?> relatedRepoInfo = GeneratedMetadata.getByEntityClass(targetType);
        if (relatedRepoInfo == null)
            throw new IllegalStateException("Unknown repository for type " + targetType);

        String relationName = relatedRepoInfo.getManyToOneFieldNames()
                .get(repositoryModel.getEntityClass().getName());
        if (relationName == null) {
            throw new IllegalStateException(
                    "No ManyToOne back-reference found in " + targetType.getSimpleName() +
                            " pointing to " + repositoryModel.getEntityClass().getSimpleName()
            );
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, relatedRepoInfo);
        if (adapter == null)
            throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getEntityClass());

        if (!field.lazy()) {
            List<Object> results = loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey, policy);
            if (autoDeepPrefetch && !results.isEmpty())
                autoDeepPrefetchRelated(results, relatedRepoInfo, 1);
            return results;
        }

        return new LazyArrayList<>(() -> {
            List<Object> results = loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey, policy);
            if (autoDeepPrefetch && !results.isEmpty())
                autoDeepPrefetchRelated(results, relatedRepoInfo, 1);
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

        List<Object> immutable = (result == null || result.isEmpty())
                ? Collections.emptyList()
                : List.copyOf(result);

        if (policy.allowStale()) {
            queryResultCache.put(queryKey, immutable);
            putCached(cacheKey, immutable);
        }

        return immutable;
    }

    private void batchLoadOneToOne(FieldModel<T> field, List<ID> parentIds) {
        if (parentIds.isEmpty()) return;
        if (parentIds.size() <= inListChunkSize) {
            batchLoadOneToOneChunk(field, parentIds);
            return;
        }
        for (List<ID> chunk : partition(parentIds, inListChunkSize)) {
            batchLoadOneToOneChunk(field, chunk);
        }
    }

    private void batchLoadOneToOneChunk(FieldModel<T> field, List<ID> parentIds) {
        RepositoryModel<?, ?> target = GeneratedMetadata.getByEntityClass(field.type());
        if (target == null)
            throw new IllegalStateException("Unknown repository for type " + field.type());

        FieldModel<Object> backRef = (FieldModel<Object>) target.getOneToOneBackReferences()
                .get(repositoryModel.getEntityClass().getName());
        if (backRef == null) {
            Logging.error("No OneToOne back-reference for field: " + field.name());
            return;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, target);
        if (adapter == null) {
            Logging.error("No adapter found for type: " + field.type());
            return;
        }

        SelectQuery  query   = Query.select().where(backRef.columnName()).in(parentIds).build();
        List<Object> results = adapter.find(query, policyFor(field));
        Map<ID, Object> mapped = new HashMap<>(results.size());

        for (Object obj : results) {
            ID parentId = (ID) backRef.getValue(obj);
            if (mapped.put(parentId, obj) != null) {
                throw new IllegalStateException(
                        "Multiple one-to-one results for field " + field.name());
            }
        }

        for (ID id : parentIds) {
            // Always store something so a second lookup knows the DB was queried
            putCached(buildCacheKey(field.name(), id),
                    mapped.getOrDefault(id, NULL_MARKER));
        }

        incrementQueryCount(field.name());
    }

    private void batchLoadOneToMany(FieldModel<T> field, List<ID> parentIds) {
        if (parentIds.isEmpty()) return;
        if (parentIds.size() <= inListChunkSize) {
            batchLoadOneToManyChunk(field, parentIds);
            return;
        }
        // Merge results across chunks into a single grouped map.
        Map<ID, List<Object>> grouped = new HashMap<>(parentIds.size());
        for (List<ID> chunk : partition(parentIds, inListChunkSize)) {
            mergeOneToManyChunk(field, chunk, grouped);
        }
        for (ID id : parentIds) {
            List<Object> children = grouped.getOrDefault(id, Collections.emptyList());
            putCached(buildCacheKey(field.name(), id), List.copyOf(children));
        }
        incrementQueryCount(field.name());
    }

    private void batchLoadOneToManyChunk(FieldModel<T> field, List<ID> parentIds) {
        mergeAndCacheOneToMany(field, parentIds, null);
        incrementQueryCount(field.name());
    }

    /**
     * Executes one IN query for the chunk and either writes directly to the cache
     * (when {@code accumulator} is null) or merges into the accumulator map.
     */
    private void mergeOneToManyChunk(
            FieldModel<T> field,
            List<ID> chunk,
            Map<ID, List<Object>> accumulator
    ) {
        mergeAndCacheOneToMany(field, chunk, accumulator);
    }

    private void mergeAndCacheOneToMany(
            FieldModel<T> field,
            List<ID> ids,
            @Nullable Map<ID, List<Object>> accumulator
    ) {
        Class<?> targetType = field.elementType();
        if (targetType == null)
            throw new IllegalStateException("OneToMany field must have elementType: " + field.name());

        RepositoryModel<?, ?> related = GeneratedMetadata.getByEntityClass(targetType);
        if (related == null)
            throw new IllegalStateException("Unknown repository for type " + targetType);

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, related);
        if (adapter == null)
            throw new IllegalStateException("No adapter found for type: " + targetType);

        String relationName = related.getManyToOneFieldNames()
                .get(repositoryModel.getEntityClass().getName());
        if (relationName == null) {
            throw new IllegalStateException(
                    "No ManyToOne back-reference found for OneToMany field: " + field.name());
        }

        List<Object> results = adapter.find(
                Query.select().where(relationName).in(ids).build(),
                policyFor(field)
        );

        FieldModel<Object> backRefField = (FieldModel<Object>) related.fieldByName(relationName);

        if (accumulator != null) {
            for (Object child : results) {
                ID parentId = (ID) backRefField.getValue(child);
                accumulator.computeIfAbsent(parentId, k -> new ArrayList<>(16)).add(child);
            }
        } else {
            Map<ID, List<Object>> grouped = new HashMap<>(ids.size());
            for (Object child : results) {
                ID parentId = (ID) backRefField.getValue(child);
                grouped.computeIfAbsent(parentId, k -> new ArrayList<>(16)).add(child);
            }
            for (ID id : ids) {
                List<Object> children = grouped.getOrDefault(id, Collections.emptyList());
                putCached(buildCacheKey(field.name(), id), List.copyOf(children));
            }
        }
    }

    private void batchLoadManyToOne(FieldModel<T> field, List<ID> childIds) {
        if (childIds.isEmpty()) return;
        if (childIds.size() <= inListChunkSize) {
            batchLoadManyToOneChunk(field, childIds);
            return;
        }
        for (List<ID> chunk : partition(childIds, inListChunkSize)) {
            batchLoadManyToOneChunk(field, chunk);
        }
    }

    private void batchLoadManyToOneChunk(FieldModel<T> field, List<ID> childIds) {
        RepositoryModel<Object, ?> parentInfo =
                (RepositoryModel<Object, ?>) GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null) {
            Logging.error("Unknown repository for type: " + field.type());
            return;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, parentInfo);
        if (adapter == null) {
            Logging.error("No adapter found for type: " + field.type());
            return;
        }

        List<Object> parents = adapter.find(
                Query.select().where(parentInfo.getPrimaryKey().columnName()).in(childIds).build(),
                policyFor(field)
        );

        FieldModel<Object> pkField   = parentInfo.getPrimaryKey();
        Map<ID, Object>    parentMap = new HashMap<>(parents.size());
        for (Object parent : parents) {
            ID parentId = (ID) pkField.getValue(parent);
            parentMap.put(parentId, parent);
        }

        for (ID childId : childIds) {
            Object parent = parentMap.get(childId);
            putCached(buildCacheKey(field.name(), childId),
                    parent == null ? NULL_MARKER : parent);
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

        Map<FieldModel<T>, List<ID>> oneToMany = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> oneToOne  = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> manyToOne = new HashMap<>(fields.size());

        Map<RelationshipKind, List<FieldModel<T>>> fieldIndexes = repositoryModel.getFieldIndexes();
        if (!fieldIndexes.isEmpty()) {
            for (Object parent : parents) {
                ID id = repositoryModel.getPrimaryKeyValue((T) parent);

                List<FieldModel<T>> otmFields = fieldIndexes.get(RelationshipKind.ONE_TO_MANY);
                if (otmFields != null)
                    for (FieldModel<T> f : otmFields)
                        if (fields.contains(f.name()))
                            oneToMany.computeIfAbsent(f, k -> new ArrayList<>(16)).add(id);

                List<FieldModel<T>> otoFields = fieldIndexes.get(RelationshipKind.ONE_TO_ONE);
                if (otoFields != null)
                    for (FieldModel<T> f : otoFields)
                        if (fields.contains(f.name()))
                            oneToOne.computeIfAbsent(f, k -> new ArrayList<>(16)).add(id);

                List<FieldModel<T>> mtoFields = fieldIndexes.get(RelationshipKind.MANY_TO_ONE);
                if (mtoFields != null)
                    for (FieldModel<T> f : mtoFields)
                        if (fields.contains(f.name()))
                            manyToOne.computeIfAbsent(f, k -> new ArrayList<>(16)).add(id);
            }
        } else {
            for (Object parent : parents) {
                ID id = repositoryModel.getPrimaryKeyValue((T) parent);
                for (String fieldName : fields) {
                    FieldModel<T> field = repositoryModel.fieldByName(fieldName);
                    if (field == null || !field.relationship()) continue;
                    switch (field.relationshipKind()) {
                        case ONE_TO_MANY -> oneToMany.computeIfAbsent(field, k -> new ArrayList<>(16)).add(id);
                        case ONE_TO_ONE  -> oneToOne .computeIfAbsent(field, k -> new ArrayList<>(16)).add(id);
                        case MANY_TO_ONE -> manyToOne.computeIfAbsent(field, k -> new ArrayList<>(16)).add(id);
                    }
                }
            }
        }

        prefetchAndBatchOptionallyParallel(oneToMany, oneToOne, manyToOne);
    }

    @SuppressWarnings("ObjectAllocationInLoop")
    public void prefetchDeep(Collection<Object> parents, String... dotNotationFields) {
        if (dotNotationFields.length == 0) return;

        Map<String, Set<String>> fieldPaths = new HashMap<>(dotNotationFields.length);
        for (String dotPath : dotNotationFields) {
            String[] parts     = DOT_PATTERN.split(dotPath, 2);
            String   rootField = parts[0];
            if (parts.length == 1) {
                fieldPaths.computeIfAbsent(rootField, k -> new HashSet<>(4));
            } else {
                fieldPaths.computeIfAbsent(rootField, k -> new HashSet<>(4)).add(parts[1]);
            }
        }
        prefetchDeep(parents, fieldPaths);
    }

    public void prefetchDeep(Collection<Object> parents, Map<String, Set<String>> fieldPaths) {
        if (fieldPaths.isEmpty()) return;

        prefetch(parents, fieldPaths.keySet());

        for (Map.Entry<String, Set<String>> entry : fieldPaths.entrySet()) {
            Set<String> nestedFields = entry.getValue();
            if (nestedFields.isEmpty()) continue;

            FieldModel<T> field = repositoryModel.fieldByName(entry.getKey());
            if (field == null || !field.relationship()) continue;

            List<Object> relatedEntities = new ArrayList<>(parents.size() * 4);
            for (Object parent : parents) {
                ID     parentId = repositoryModel.getPrimaryKeyValue((T) parent);
                String cacheKey = buildCacheKey(field.name(), parentId);
                Object related  = relationshipCache.get(cacheKey);

                if (related == null || related == NULL_MARKER) continue;

                if (related instanceof Collection<?> col) {
                    relatedEntities.addAll(col);
                } else {
                    relatedEntities.add(related);
                }
            }

            if (relatedEntities.isEmpty()) continue;

            Class<?> relatedType = field.relationshipKind() == RelationshipKind.ONE_TO_MANY
                    ? field.elementType()
                    : field.type();
            if (relatedType == null) continue;

            RepositoryModel<?, ?> relatedModel = GeneratedMetadata.getByEntityClass(relatedType);
            if (relatedModel == null) continue;

            RelationshipHandler<?, ?> relatedHandler = getRelatedHandler(relatedModel);

            Map<String, Set<String>> nestedPaths = new HashMap<>(nestedFields.size());
            for (String nf : nestedFields) nestedPaths.put(nf, Set.of());

            if (relatedHandler instanceof AbstractRelationshipHandler<?, ?> abstractHandler) {
                abstractHandler.prefetchDeep(relatedEntities, nestedPaths);
            } else if (relatedHandler != null) {
                relatedHandler.prefetch(relatedEntities, nestedFields);
            }
        }
    }

    protected static RelationshipHandler<?, ?> getRelatedHandler(RepositoryModel<?, ?> relatedModel) {
        RepositoryAdapter<?, ?, ?> adapter = RepositoryRegistry.get(relatedModel.getEntityClass());
        Objects.requireNonNull(adapter);
        return adapter.getRelationshipHandler();
    }

    public void warmCache(List<ID> ids, Set<String> anticipatedFields) {
        if (ids.isEmpty() || anticipatedFields.isEmpty()) return;

        Logging.deepInfo(() -> "Warming cache for " + ids.size()
                + " entities, " + anticipatedFields.size() + " fields");

        Map<FieldModel<T>, List<ID>> oneToManyFields = new HashMap<>(anticipatedFields.size());
        Map<FieldModel<T>, List<ID>> oneToOneFields  = new HashMap<>(anticipatedFields.size());
        Map<FieldModel<T>, List<ID>> manyToOneFields = new HashMap<>(anticipatedFields.size());

        for (String fieldName : anticipatedFields) {
            FieldModel<T> field = repositoryModel.fieldByName(fieldName);
            if (field == null || !field.relationship()) continue;
            switch (field.relationshipKind()) {
                case ONE_TO_MANY -> oneToManyFields.put(field, ids);
                case ONE_TO_ONE  -> oneToOneFields .put(field, ids);
                case MANY_TO_ONE -> manyToOneFields.put(field, ids);
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
                @SuppressWarnings("unchecked")
                CompletableFuture<Void>[] futures = new CompletableFuture[3];
                futures[0] = CompletableFuture.runAsync(
                        () -> oneToManyFields.forEach(this::batchLoadOneToMany),  parallelExecutor);
                futures[1] = CompletableFuture.runAsync(
                        () -> oneToOneFields .forEach(this::batchLoadOneToOne),   parallelExecutor);
                futures[2] = CompletableFuture.runAsync(
                        () -> manyToOneFields.forEach(this::batchLoadManyToOne),  parallelExecutor);

                CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel prefetch interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException("Parallel prefetch failed", e);
            }
        } else {
            oneToManyFields.forEach(this::batchLoadOneToMany);
            oneToOneFields .forEach(this::batchLoadOneToOne);
            manyToOneFields.forEach(this::batchLoadManyToOne);
        }
    }

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
        String relationName = relatedRepoInfo.getManyToOneFieldNames()
                .get(repositoryModel.getEntityClass().getName());

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, relatedRepoInfo);
        Objects.requireNonNull(adapter);

        return (int) adapter.count(
                Query.select().where(relationName).eq(primaryKeyValue).build(),
                policyFor(field)
        );
    }

    @Override
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
        Arrays.fill(cacheKeyPool, null);
    }

    public void clearAll() { clear(); }

    public static void clearThreadLocalCache() { l1Cache.remove(); }

    // -----------------------------------------------------------------------
    // Cache internals
    // -----------------------------------------------------------------------

    private static Map<String, Object> getOrCreateL1Cache() {
        WeakReference<Map<String, Object>> ref = l1Cache.get();
        Map<String, Object> map = ref.get();
        if (map == null) {
            map = newBoundedL1Map();
            l1Cache.set(new WeakReference<>(map));
        }
        return map;
    }

    /**
     * Returns the raw cached value including {@link #NULL_MARKER}, or {@code null}
     * if nothing is cached. Callers that need to expose values publicly must pass
     * the result through {@link #unbox(Object)}.
     */
    @Nullable
    private Object getCachedRaw(String cacheKey, ReadPolicy policy) {
        if (!policy.allowStale()) return null;

        Map<String, Object> l1     = getOrCreateL1Cache();
        Object              l1Result = l1.get(cacheKey);
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

    /**
     * Converts a raw cache value to its public form:
     * {@link #NULL_MARKER} → {@code null}, anything else → itself.
     */
    @Nullable
    private static Object unbox(Object raw) {
        return raw == NULL_MARKER ? null : raw;
    }

    private void putCached(String cacheKey, Object value) {
        relationshipCache.put(cacheKey, value);
        getOrCreateL1Cache().put(cacheKey, value);
    }

    // -----------------------------------------------------------------------
    // Adapter cache
    // -----------------------------------------------------------------------

    @Nullable
    private static RepositoryAdapter<Object, Object, ?> resolveAdapterCached(
            @NotNull FieldModel<?> field,
            @NotNull RepositoryModel<?, ?> targetInfo
    ) {
        String key = field.name() + ":" + targetInfo.getEntityClass().getName();

        String adapterName = field.externalRepository();
        RepositoryAdapter<Object, Object, ?> resolved;

        if (adapterName != null) {
            resolved = RepositoryRegistry.get(adapterName);
            if (resolved == null) {
                Logging.error("External adapter '" + adapterName
                        + "' not found in RepositoryRegistry for field " + field.name());
                return null;
            }
            Logging.deepInfo(() -> "Using external adapter '" + adapterName
                    + "' for field " + field.name());
        } else {
            resolved = (RepositoryAdapter<Object, Object, ?>)
                    RepositoryRegistry.get(targetInfo.getEntityClass());
        }

        return resolved;
    }

    @NotNull
    private String buildCacheKey(@NotNull String fieldName, Object id) {
        if (id instanceof Integer || id instanceof Long) {
            String key       = entityPrefix + id + ":" + fieldName;
            int    poolIndex = (key.hashCode() & Integer.MAX_VALUE) % cacheKeyPoolSizeInstance;

            String pooled = cacheKeyPool[poolIndex];
            if (pooled == null || !pooled.equals(key)) {
                cacheKeyPool[poolIndex] = key.intern();
                return cacheKeyPool[poolIndex];
            }
            return pooled;
        }
        return entityPrefix + id + ":" + fieldName;
    }

    private static <E> List<List<E>> partition(List<E> list, int size) {
        List<List<E>> parts = new ArrayList<>((list.size() + size - 1) / size);
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }

    private void incrementQueryCount(String fieldName) {
        queryCountByField.computeIfAbsent(fieldName, k -> new AtomicLong()).incrementAndGet();
    }

    public double getCacheHitRate() {
        long l1Hits = l1CacheHits.get();
        long l2Hits = l2CacheHits.get();
        long misses = cacheMisses.get();
        long total  = l1Hits + l2Hits + misses;
        return total == 0 ? 0.0 : (double) (l1Hits + l2Hits) / total;
    }

    public RelationshipMetrics getMetrics() {
        long l1Hits = l1CacheHits.get();
        long l2Hits = l2CacheHits.get();
        long misses = cacheMisses.get();

        Map<String, Long> queryCountsCopy = new HashMap<>(queryCountByField.size());
        queryCountByField.forEach((f, c) -> queryCountsCopy.put(f, c.get()));

        Map<String, Object> l1Cache = AbstractRelationshipHandler.l1Cache.get().get();
        Map<String, Object> l2Cache = relationshipCache;

        Map<String, Object> l1 = getOrCreateL1Cache();

        return new RelationshipMetrics(
                misses,
                l1Hits,
                l2Hits,
                l1Hits + l2Hits,
                l2Cache == null ? 0 : l2Cache.size(),
                l1Cache == null ? 0 : l1Cache.size(),
                queryResultCache.size(),
                queryCountsCopy,
                parallelPrefetchEnabled,
                autoWarmCache,
                autoDeepPrefetch,
                autoDeepPrefetchDepth
        );
    }

    public void resetMetrics() {
        cacheMisses.set(0);
        l1CacheHits.set(0);
        l2CacheHits.set(0);
        queryCountByField.clear();
    }

    private void autoDeepPrefetchRelated(
            List<Object> entities,
            RepositoryModel<?, ?> model,
            int currentDepth
    ) {
        if (entities.isEmpty() || currentDepth >= autoDeepPrefetchDepth) return;

        RelationshipHandler<?, ?> handler = getRelatedHandler(model);
        if (!(handler instanceof AbstractRelationshipHandler<?, ?> abstractHandler)) return;

        List<? extends RelationshipModel<?, ?>> relationships = model.getRelationships();
        Set<String> relationshipFields = new HashSet<>(relationships.size());
        for (RelationshipModel<?, ?> r : relationships) {
            relationshipFields.add(r.getFieldModel().name());
        }

        if (relationshipFields.isEmpty()) return;

        Logging.deepInfo(() -> "Auto-deep prefetch at depth " + currentDepth
                + " for " + entities.size() + " " + model.entitySimpleName()
                + " entities, fields: " + relationshipFields);

        abstractHandler.prefetch(entities, relationshipFields);
    }

    /**
     * Immutable snapshot of relationship handler performance counters.
     */
    public record RelationshipMetrics(
            long cacheMisses,
            long l1CacheHits,
            long l2CacheHits,
            long totalCacheHits,
            int  l2CacheSize,
            int  l1CacheSize,
            int  queryResultCacheSize,
            Map<String, Long> queryCountsByField,
            boolean parallelPrefetchEnabled,
            boolean autoWarmCache,
            boolean autoDeepPrefetch,
            int     autoDeepPrefetchDepth
    ) {
        public double overallHitRate() {
            long total = totalCacheHits + cacheMisses;
            return total == 0 ? 0.0 : (double) totalCacheHits / total;
        }

        public double l1HitRate() {
            return totalCacheHits == 0 ? 0.0 : (double) l1CacheHits / totalCacheHits;
        }

        public double l2HitRate() {
            return totalCacheHits == 0 ? 0.0 : (double) l2CacheHits / totalCacheHits;
        }
    }
}