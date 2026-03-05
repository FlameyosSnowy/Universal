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
    protected final RepositoryModel<T, ID> repositoryModel;
    protected final Class<ID> idClass;
    protected final TypeResolverRegistry resolverRegistry;

    private final ExecutorService parallelExecutor;

    // Static caches shared across all instances
    private static final Map<String, String> nameCache = new ConcurrentHashMap<>(16);

    // L2 cache: shared across threads
    private final Map<String, Object> relationshipCache = new ConcurrentHashMap<>(64);

    // L1 cache: thread-local for ultra-fast access
    private static final ThreadLocal<WeakReference<Map<String, Object>>> l1Cache =
        ThreadLocal.withInitial(() -> new WeakReference<>(new HashMap<>(64)));

    // Adapter cache to eliminate redundant lookups
    private final Map<String, RepositoryAdapter<Object, Object, ?>> adapterCache =
        new ConcurrentHashMap<>(32);

    // Query result cache for reusing identical queries
    private final Map<String, List<Object>> queryResultCache = new ConcurrentHashMap<>(64);

    // Cache key pool for reducing string allocations
    private static final int CACHE_KEY_POOL_SIZE = 1024;
    private final String[] cacheKeyPool = new String[CACHE_KEY_POOL_SIZE];

    // Adaptive batch sizing
    private static final int MIN_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 1000;
    private final Map<String, Integer> optimalBatchSizes = new ConcurrentHashMap<>(32);

    // Performance metrics
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong l1CacheHits = new AtomicLong();
    private final AtomicLong l2CacheHits = new AtomicLong();
    private final Map<String, AtomicLong> queryCountByField = new ConcurrentHashMap<>(32);

    private static final Object NULL_MARKER = new Object();

    private final String entityPrefix;

    // Configuration
    private volatile boolean parallelPrefetchEnabled = false;
    private volatile int prefetchThreadPoolSize = Runtime.getRuntime().availableProcessors();
    private volatile boolean autoWarmCache = false;
    private volatile boolean autoDeepPrefetch = false;
    private volatile int autoDeepPrefetchDepth = 2;

    private static final Pattern PATTERN = Pattern.compile("\\.");

    protected AbstractRelationshipHandler(
        RepositoryModel<T, ID> repositoryModel,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry
    ) {
        this.repositoryModel = repositoryModel;
        this.idClass = idClass;
        this.resolverRegistry = resolverRegistry;
        this.entityPrefix = repositoryModel.entitySimpleName() + ":";
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

    // ========== Configuration Methods ==========

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

    /**
     * Get the current parallel prefetch configuration.
     *
     * @return true if parallel prefetch is enabled
     */
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
        if (size < 1) {
            throw new IllegalArgumentException("Thread pool size must be at least 1");
        }
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

    /**
     * Get the current auto-warm cache configuration.
     *
     * @return true if auto-warm cache is enabled
     */
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

    /**
     * Get the current auto-deep prefetch configuration.
     *
     * @return true if auto-deep prefetch is enabled
     */
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
        if (depth < 1) {
            throw new IllegalArgumentException("Depth must be at least 1");
        }
        this.autoDeepPrefetchDepth = depth;
    }

    /**
     * Get the current auto-deep prefetch depth.
     *
     * @return the maximum depth for automatic deep prefetch
     */
    public int getAutoDeepPrefetchDepth() {
        return autoDeepPrefetchDepth;
    }

    // ========== Core Relationship Handling ==========

    private static ReadPolicy policyFor(@NotNull FieldModel<?> field) {
        return switch (field.consistency()) {
            case STRONG -> ReadPolicy.STRONG_READ_POLICY;
            case EVENTUAL -> ReadPolicy.EVENTUAL_READ_POLICY;
            case NONE -> ReadPolicy.NO_READ_POLICY;
        };
    }

    @Override
    public @Nullable Object handleManyToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        ReadPolicy policy = policyFor(field);
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);

        // Check tiered cache
        Object cached = getCached(cacheKey, policy);
        if (cached != null) {
            return cached == NULL_MARKER ? null : cached;
        }

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

        RepositoryModel<?, ?> parentInfo = GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null) {
            throw new IllegalStateException("Unknown repository for type " + field.type());
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, parentInfo);
        if (adapter == null) {
            throw new IllegalStateException("Missing adapter for " + parentInfo.getEntityClass());
        }

        SelectQuery query = Query.select()
            .where(repositoryModel.getPrimaryKey().columnName()).eq(primaryKeyValue)
            .limit(1)
            .build();

        List<Object> result = adapter.find(query, policy);
        Object value = result.isEmpty() ? null : result.getFirst();

        if (policy.allowStale()) {
            putCached(cacheKey, value == null ? NULL_MARKER : value);
        }

        // Auto deep prefetch if enabled
        if (autoDeepPrefetch && value != null) {
            autoDeepPrefetchRelated(List.of(value), parentInfo, 1);
        }

        return value;
    }

    @Override
    public @Nullable Object handleOneToOneRelationship(ID primaryKeyValue, @NotNull FieldModel<T> field) {
        ReadPolicy policy = policyFor(field);
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);

        // Check tiered cache
        Object cached = getCached(cacheKey, policy);
        if (cached != null) {
            return cached == NULL_MARKER ? null : cached;
        }

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

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
            putCached(cacheKey, NULL_MARKER);
            return null;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, targetInfo);
        if (adapter == null) {
            Logging.error("Missing adapter for type: " + targetType.getName());
            putCached(cacheKey, NULL_MARKER);
            return null;
        }

        SelectQuery query = Query.select()
            .where(backRef.columnName()).eq(primaryKeyValue)
            .limit(1)
            .build();

        List<Object> results = adapter.find(query, policy);
        Object result = (results == null || results.isEmpty()) ? null : results.getFirst();

        if (policy.allowStale()) {
            putCached(cacheKey, result == null ? NULL_MARKER : result);
        }

        // Auto deep prefetch if enabled
        if (autoDeepPrefetch && result != null) {
            autoDeepPrefetchRelated(List.of(result), targetInfo, 1);
        }

        return result;
    }

    @Override
    public List<Object> handleOneToManyRelationship(ID primaryKeyValue, FieldModel<T> field) {
        ReadPolicy policy = policyFor(field);

        // Check cache first
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);
        Object cached = getCached(cacheKey, policy);
        if (cached != null) {
            return (List<Object>) cached;
        }

        cacheMisses.incrementAndGet();
        incrementQueryCount(field.name());

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

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, relatedRepoInfo);
        if (adapter == null) {
            throw new IllegalStateException("Missing adapter for " + relatedRepoInfo.getEntityClass());
        }

        if (!field.lazy()) {
            List<Object> results = loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey, policy);

            // Auto deep prefetch if enabled
            if (autoDeepPrefetch && !results.isEmpty()) {
                autoDeepPrefetchRelated(results, relatedRepoInfo, 1);
            }

            return results;
        }

        // Lazy loading
        return new LazyArrayList<>(() -> {
            List<Object> results = loadOneToManyResults(primaryKeyValue, adapter, relationName, cacheKey, policy);

            // Auto deep prefetch if enabled (on lazy load)
            if (autoDeepPrefetch && !results.isEmpty()) {
                autoDeepPrefetchRelated(results, relatedRepoInfo, 1);
            }

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
        // Check if we've already executed this exact query
        String queryKey = relationName + "=" + primaryKeyValue;

        if (policy.allowStale()) {
            List<Object> cachedQuery = queryResultCache.get(queryKey);
            if (cachedQuery != null) {
                putCached(cacheKey, cachedQuery);
                return cachedQuery;
            }
        }

        List<Object> result = adapter.find(
            Query.select()
                .where(relationName).eq(primaryKeyValue)
                .build(),
            policy
        );

        List<Object> immutable = result == null ? Collections.emptyList() : List.copyOf(result);

        if (policy.allowStale()) {
            queryResultCache.put(queryKey, immutable);
            putCached(cacheKey, immutable);
        }

        return immutable;
    }

    // ========== Helper Methods ==========

    /**
     * Automatically deep prefetch relationships for loaded entities.
     * Called internally when autoDeepPrefetch is enabled.
     *
     * @param entities Entities to prefetch relationships for
     * @param model Repository model of the entities
     * @param currentDepth Current recursion depth
     */
    private void autoDeepPrefetchRelated(List<Object> entities, RepositoryModel<?, ?> model, int currentDepth) {
        if (entities.isEmpty() || currentDepth >= autoDeepPrefetchDepth) {
            return;
        }

        // Get the handler for this entity type
        RelationshipHandler<?, ?> handler = getRelatedHandler(model);
        if (!(handler instanceof AbstractRelationshipHandler<?, ?> abstractHandler)) {
            return;
        }

        // Collect all relationship fields
        List<? extends RelationshipModel<?, ?>> relationships = model.getRelationships();
        Set<String> relationshipFields = new HashSet<>(relationships.size());
        for (RelationshipModel<?, ?> field : relationships) {
            relationshipFields.add(field.getFieldModel().name());
        }

        if (relationshipFields.isEmpty()) {
            return;
        }

        Logging.deepInfo(() -> "Auto-deep prefetch at depth " + currentDepth +
            " for " + entities.size() + " " + model.entitySimpleName() +
            " entities, fields: " + relationshipFields);

        // Prefetch all relationships for these entities
        abstractHandler.prefetch(entities, relationshipFields);
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
     * Results are cached to avoid repeated lookups.
     */
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

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, target);
        if (adapter == null) {
            Logging.error("No adapter found for type: " + field.type());
            return;
        }

        SelectQuery query = Query.select()
            .where(backRef.columnName()).in(parentIds)
            .build();

        List<Object> results = adapter.find(query, policyFor(field));
        Map<ID, Object> mapped = new HashMap<>(results.size());

        for (Object obj : results) {
            ID parentId = (ID) backRef.getValue(obj);
            if (mapped.put(parentId, obj) != null) {
                throw new IllegalStateException(
                    "Multiple one-to-one results for field " + field.name()
                );
            }
        }

        for (ID id : parentIds) {
            putCached(
                buildCacheKey(field.name(), id),
                mapped.getOrDefault(id, NULL_MARKER)
            );
        }

        incrementQueryCount(field.name());
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

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, related);
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
                .where(relationName).in(parentIds)
                .build(),
            policyFor(field)
        );

        Map<ID, List<Object>> grouped = new HashMap<>(parentIds.size());
        FieldModel<Object> backRefField = (FieldModel<Object>) related.fieldByName(relationName);

        for (Object child : results) {
            ID parentId = (ID) backRefField.getValue(child);
            //noinspection ObjectAllocationInLoop
            grouped.computeIfAbsent(parentId, k -> new ArrayList<>(16)).add(child);
        }

        for (ID id : parentIds) {
            List<Object> list = grouped.getOrDefault(id, List.of());
            putCached(buildCacheKey(field.name(), id), List.copyOf(list));
        }

        incrementQueryCount(field.name());
    }

    private void batchLoadManyToOne(FieldModel<T> field, List<ID> childIds) {
        RepositoryModel<Object, ?> parentInfo = (RepositoryModel<Object, ?>) GeneratedMetadata.getByEntityClass(field.type());
        if (parentInfo == null) {
            Logging.error("Unknown repository for type: " + field.type());
            return;
        }

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, parentInfo);
        if (adapter == null) {
            Logging.error("No adapter found for type: " + field.type());
            return;
        }

        // Fetch all parent entities in one query
        List<Object> parents = adapter.find(
            Query.select()
                .where(parentInfo.getPrimaryKey().columnName()).in(childIds)
                .build(),
            policyFor(field)
        );

        // Map results to cache
        FieldModel<Object> pkField = parentInfo.getPrimaryKey();
        Map<ID, Object> parentMap = new HashMap<>(parents.size());

        for (Object parent : parents) {
            ID parentId = (ID) pkField.getValue(parent);
            parentMap.put(parentId, parent);
        }

        // Cache each result
        for (ID childId : childIds) {
            Object parent = parentMap.get(childId);
            putCached(
                buildCacheKey(field.name(), childId),
                parent == null ? NULL_MARKER : parent
            );
        }

        incrementQueryCount(field.name());
    }

    @SuppressWarnings("ObjectAllocationInLoop")
    @Override
    public void prefetch(Collection<Object> parents, Set<String> fields) {
        // Collect all parent IDs first
        List<ID> parentIds = new ArrayList<>(parents.size());
        for (Object parent : parents) {
            parentIds.add(repositoryModel.getPrimaryKeyValue((T) parent));
        }

        // If auto-warm is enabled, warm the cache first with just the IDs
        // This can be more efficient as it avoids loading parent entities multiple times
        if (autoWarmCache && !parentIds.isEmpty()) {
            Logging.deepInfo(() -> "Auto-warming cache for " + parentIds.size() + " entities");
            warmCache(parentIds, fields);
            return; // Cache is already warmed, no need to continue
        }

        // Standard prefetch logic
        Map<FieldModel<T>, List<ID>> oneToMany = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> oneToOne = new HashMap<>(fields.size());
        Map<FieldModel<T>, List<ID>> manyToOne = new HashMap<>(fields.size());

        for (Object parent : parents) {
            ID id = repositoryModel.getPrimaryKeyValue((T) parent);

            for (String fieldName : fields) {
                FieldModel<T> field = repositoryModel.fieldByName(fieldName);
                if (field == null || !field.relationship()) {
                    continue;
                }

                switch (field.relationshipKind()) {
                    case ONE_TO_MANY -> oneToMany
                        .computeIfAbsent(field, f -> new ArrayList<>(16))
                        .add(id);
                    case ONE_TO_ONE -> oneToOne
                        .computeIfAbsent(field, f -> new ArrayList<>(16))
                        .add(id);
                    case MANY_TO_ONE -> manyToOne
                        .computeIfAbsent(field, f -> new ArrayList<>(16))
                        .add(id);
                }
            }
        }

        // Execute based on configuration
        prefetchAndBatchOptionallyParallel(oneToMany, oneToOne, manyToOne);
    }

    /**
     * Deep prefetch with recursive relationship loading using dot notation.
     * Convenience method that automatically builds the nested field path structure.
     * <p>
     * Example: prefetchDeep(users, "faction.warp", "faction.members", "posts.comments.author")
     *
     * @param parents Parent entities to prefetch for
     * @param dotNotationFields Field paths in dot notation (e.g., "faction.warp.location")
     */
    @SuppressWarnings("ObjectAllocationInLoop")
    public void prefetchDeep(Collection<Object> parents, String... dotNotationFields) {
        if (dotNotationFields.length == 0) return;

        // Build the nested field path map from dot notation
        Map<String, Set<String>> fieldPaths = new HashMap<>(dotNotationFields.length);

        for (String dotPath : dotNotationFields) {
            String[] parts = PATTERN.split(dotPath, 2);
            String rootField = parts[0];

            if (parts.length == 1) {
                // Simple field with no nesting
                fieldPaths.computeIfAbsent(rootField, k -> new HashSet<>(32));
            } else {
                // Nested field path - recursively add remaining path
                String remainingPath = parts[1];
                fieldPaths.computeIfAbsent(rootField, k -> new HashSet<>(32)).add(remainingPath);
            }
        }

        // Call the main prefetchDeep implementation
        prefetchDeep(parents, fieldPaths);
    }

    /**
     * Deep prefetch with recursive relationship loading.
     * Loads multiple levels of relationships in a single call.
     *
     * @param parents Parent entities to prefetch for
     * @param fieldPaths Map of field names to their nested fields
     *                   Example: {"faction" -> {"warp", "members"}, "posts" -> {"comments"}}
     */
    public void prefetchDeep(Collection<Object> parents, Map<String, Set<String>> fieldPaths) {
        if (fieldPaths.isEmpty()) return;

        // First prefetch immediate relationships
        prefetch(parents, fieldPaths.keySet());

        // Then recursively prefetch nested relationships
        Map<String, Set<String>> nestedPaths = new HashMap<>(32);
        List<Object> relatedEntities = new ArrayList<>(32);
        for (Map.Entry<String, Set<String>> entry : fieldPaths.entrySet()) {
            Set<String> nestedFields = entry.getValue();
            if (nestedFields.isEmpty()) continue;

            FieldModel<T> field = repositoryModel.fieldByName(entry.getKey());
            if (field == null || !field.relationship()) continue;

            // Collect all related entities for next level
            for (Object parent : parents) {
                ID parentId = repositoryModel.getPrimaryKeyValue((T) parent);
                String cacheKey = buildCacheKey(field.name(), parentId);
                Object related = relationshipCache.get(cacheKey);

                if (related != null && related != NULL_MARKER) {
                    if (related instanceof Collection) {
                        relatedEntities.addAll((Collection<?>) related);
                    } else {
                        relatedEntities.add(related);
                    }
                }
            }

            // Recursively prefetch for nested relationships
            if (!relatedEntities.isEmpty()) {
                Class<?> relatedType = field.relationshipKind() == RelationshipKind.ONE_TO_MANY
                    ? field.elementType()
                    : field.type();

                if (relatedType == null) continue;

                RepositoryModel<?, ?> relatedModel = GeneratedMetadata.getByEntityClass(relatedType);
                if (relatedModel != null) {
                    // Get the handler for the related type
                    RelationshipHandler<?, ?> relatedHandler = getRelatedHandler(relatedModel);

                    if (relatedHandler instanceof AbstractRelationshipHandler<?, ?> abstractHandler) {
                        for (String nestedField : nestedFields) {
                            nestedPaths.put(nestedField, Set.of());
                        }

                        abstractHandler.prefetchDeep(relatedEntities, nestedPaths);
                    } else {
                        // Fallback to simple prefetch if handler doesn't support deep prefetch
                        if (relatedHandler != null) {
                            relatedHandler.prefetch(relatedEntities, nestedFields);
                        }
                    }
                }
                nestedFields.clear();
                relatedEntities.clear();
            }
        }
    }

    /**
     * Get the relationship handler for a related entity type.
     * This method should be overridden by implementations that maintain
     * a registry of handlers, or it will attempt to use the resolver registry.
     *
     * @param relatedModel The repository model of the related entity
     * @return The relationship handler for the related entity, or null if not found
     */
    protected static RelationshipHandler<?, ?> getRelatedHandler(RepositoryModel<?, ?> relatedModel) {
        // Try to get from RepositoryRegistry
        RepositoryAdapter<?, ?, ?> adapter = RepositoryRegistry.get(relatedModel.getEntityClass());
        Objects.requireNonNull(adapter);

        return adapter.getRelationshipHandler();
    }

    /**
     * Warm cache for anticipated access patterns.
     * Useful for preloading data before processing a batch of entities.
     * This is more efficient than prefetch() when you only have IDs, not entity objects.
     *
     * @param ids Entity IDs to warm cache for
     * @param anticipatedFields Field names that will likely be accessed
     */
    public void warmCache(List<ID> ids, Set<String> anticipatedFields) {
        if (ids.isEmpty() || anticipatedFields.isEmpty()) return;

        Logging.deepInfo(() -> "Warming cache for " + ids.size() +
            " entities, " + anticipatedFields.size() + " fields");

        // Group fields by relationship type and assign the IDs list to each
        Map<FieldModel<T>, List<ID>> oneToManyFields = new HashMap<>(anticipatedFields.size());
        Map<FieldModel<T>, List<ID>> oneToOneFields = new HashMap<>(anticipatedFields.size());
        Map<FieldModel<T>, List<ID>> manyToOneFields = new HashMap<>(anticipatedFields.size());

        for (String fieldName : anticipatedFields) {
            FieldModel<T> field = repositoryModel.fieldByName(fieldName);
            if (field == null || !field.relationship()) continue;

            // Each field gets the same list of IDs
            switch (field.relationshipKind()) {
                case ONE_TO_MANY -> oneToManyFields.put(field, ids);
                case ONE_TO_ONE -> oneToOneFields.put(field, ids);
                case MANY_TO_ONE -> manyToOneFields.put(field, ids);
            }
        }

        // Batch load all at once (respecting parallel configuration)
        prefetchAndBatchOptionallyParallel(oneToManyFields, oneToOneFields, manyToOneFields);
    }

    private void prefetchAndBatchOptionallyParallel(Map<FieldModel<T>, List<ID>> oneToManyFields, Map<FieldModel<T>, List<ID>> oneToOneFields, Map<FieldModel<T>, List<ID>> manyToOneFields) {
        if (parallelPrefetchEnabled) {
            try {
                CompletableFuture<Void>[] futures = new CompletableFuture[3];
                futures[0] = CompletableFuture.runAsync(() ->
                    oneToManyFields.forEach(this::batchLoadOneToMany), parallelExecutor
                );
                futures[1] = CompletableFuture.runAsync(() ->
                    oneToOneFields.forEach(this::batchLoadOneToOne), parallelExecutor
                );
                futures[2] = CompletableFuture.runAsync(() ->
                    manyToOneFields.forEach(this::batchLoadManyToOne), parallelExecutor
                );

                CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Parallel prefetch interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException("Parallel prefetch failed", e);
            }
        } else {
            // Sequential execution
            oneToManyFields.forEach(this::batchLoadOneToMany);
            oneToOneFields.forEach(this::batchLoadOneToOne);
            manyToOneFields.forEach(this::batchLoadManyToOne);
        }
    }

    // Add cleanup method
    public void shutdown() {
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

    /**
     * Get the size of a OneToMany relationship without loading the full collection.
     * More efficient than loading all entities just to count them.
     *
     * @param primaryKeyValue The parent entity ID
     * @param field The OneToMany field
     * @return The number of related entities
     */
    public int getRelationshipSize(ID primaryKeyValue, FieldModel<T> field) {
        if (field.relationshipKind() != RelationshipKind.ONE_TO_MANY) {
            throw new IllegalArgumentException("Only ONE_TO_MANY supports size queries");
        }

        // Check if we already have the collection cached
        String cacheKey = buildCacheKey(field.name(), primaryKeyValue);
        Object cached = relationshipCache.get(cacheKey);
        if (cached instanceof List) {
            return ((List<?>) cached).size();
        }

        Class<?> targetType = field.elementType();
        RepositoryModel<?, ?> relatedRepoInfo = GeneratedMetadata.getByEntityClass(targetType);
        Objects.requireNonNull(relatedRepoInfo);
        String relationName = findManyToOneFieldName(relatedRepoInfo, repositoryModel.getEntityClass());

        RepositoryAdapter<Object, Object, ?> adapter = resolveAdapterCached(field, relatedRepoInfo);
        Objects.requireNonNull(adapter);

        // Use count instead of fetching all records
        return (int) adapter.count(
            Query.select().where(relationName).eq(primaryKeyValue).build(),
            policyFor(field)
        );
    }

    // ========== Cache Management ==========

    /**
     * Builds a cache key for relationship caching with object pooling.
     */
    @NotNull
    private String buildCacheKey(@NotNull String fieldName, Object id) {
        if (id instanceof Integer || id instanceof Long) {
            String key = entityPrefix + id + ":" + fieldName;
            int hash = key.hashCode();
            int poolIndex = (hash & Integer.MAX_VALUE) % CACHE_KEY_POOL_SIZE;

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
     */
    @Nullable
    private Object getCached(String cacheKey, ReadPolicy policy) {
        if (!policy.allowStale()) {
            return null;
        }

        // Check L1 cache first (thread-local, very fast)
        Map<String, Object> l1Cache = getOrCreateL1Cache();
        Object l1Result = l1Cache.get(cacheKey);
        if (l1Result != null) {
            cacheHits.incrementAndGet();
            l1CacheHits.incrementAndGet();
            return l1Result;
        }

        // Fall back to L2 cache (shared, slower)
        Object l2Result = relationshipCache.get(cacheKey);
        if (l2Result != null) {
            cacheHits.incrementAndGet();
            l2CacheHits.incrementAndGet();
            // Promote to L1
            l1Cache.put(cacheKey, l2Result);
            return l2Result;
        }

        return null;
    }

    /**
     * Put value in both L1 and L2 cache.
     */
    private void putCached(String cacheKey, Object value) {
        relationshipCache.put(cacheKey, value);

        Map<String, Object> l1Cache = getOrCreateL1Cache();
        l1Cache.put(cacheKey, value);
    }

    /**
     * Clear thread-local L1 cache.
     * Call this periodically in long-running threads to prevent memory buildup.
     */
    public static void clearThreadLocalCache() {
        Map<String, Object> l1Cache = getOrCreateL1Cache();
        l1Cache.clear();
    }

    @Override
    public void invalidateRelationshipsForId(@NotNull ID id) {
        String prefix = entityPrefix + id + ":";
        relationshipCache.keySet().removeIf(key -> key.startsWith(prefix));

        // Also clear from L1 cache
        Map<String, Object> l1Cache = getOrCreateL1Cache();
        l1Cache.keySet().removeIf(key -> key.startsWith(prefix));

        // Clear related query results
        queryResultCache.keySet().removeIf(key -> key.contains("=" + id));
    }

    @Override
    public void clear() {
        relationshipCache.clear();
        queryResultCache.clear();

        clearThreadLocalCache();

        // Also clear adapter cache and other caches
        adapterCache.clear();
        Arrays.fill(cacheKeyPool, null);
    }

    /**
     * Clear all caches including static shared caches.
     * Use with caution as this affects all instances.
     */
    public void clearAll() {
        clear();
        nameCache.clear();
    }

    // ========== Performance Metrics ==========

    private void incrementQueryCount(String fieldName) {
        queryCountByField.computeIfAbsent(fieldName, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Get performance metrics for this handler.
     * Useful for monitoring and optimization.
     */
    public RelationshipMetrics getMetrics() {
        Map<String, Long> queryCountsCopy = new HashMap<>();
        queryCountByField.forEach((field, count) -> queryCountsCopy.put(field, count.get()));

        Map<String, Object> l1Cache = getOrCreateL1Cache();
        return new RelationshipMetrics(
            cacheHits.get(),
            cacheMisses.get(),
            l1CacheHits.get(),
            l2CacheHits.get(),
            relationshipCache.size(),
            l1Cache.size(),
            queryResultCache.size(),
            adapterCache.size(),
            queryCountsCopy,
            parallelPrefetchEnabled,
            autoWarmCache,
            autoDeepPrefetch,
            autoDeepPrefetchDepth
        );
    }

    /**
     * Reset all metrics counters.
     */
    public void resetMetrics() {
        cacheHits.set(0);
        cacheMisses.set(0);
        l1CacheHits.set(0);
        l2CacheHits.set(0);
        queryCountByField.clear();
    }

    /**
     * Container for performance metrics.
     */
    public record RelationshipMetrics(long cacheHits, long cacheMisses, long l1CacheHits, long l2CacheHits,
                                      int l2CacheSize, int l1CacheSize, int queryResultCacheSize, int adapterCacheSize,
                                      Map<String, Long> queryCountsByField, boolean parallelPrefetchEnabled,
                                      boolean autoWarmCache, boolean autoDeepPrefetch, int autoDeepPrefetchDepth) {

        public double getCacheHitRate() {
            long total = cacheHits + cacheMisses;
            return total == 0 ? 0.0 : (double) cacheHits / total;
        }

        public double getL1HitRate() {
            return cacheHits == 0 ? 0.0 : (double) l1CacheHits / cacheHits;
        }
    }
}