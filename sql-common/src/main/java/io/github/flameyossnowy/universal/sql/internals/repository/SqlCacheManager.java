package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.ReadThroughCache;
import io.github.flameyossnowy.universal.api.cache.SecondLevelCache;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class SqlCacheManager<T, ID> {

    private final DefaultResultCache<String, T, ID> cache;
    private final ObjectModel<T, ID> objectModel;
    private final boolean cacheEnabled;

    @Nullable
    private final SecondLevelCache<ID, T> l2Cache;

    @Nullable
    private final ReadThroughCache<ID, T> readThroughCache;

    public SqlCacheManager(DefaultResultCache<String, T, ID> cache, ObjectModel<T, ID> objectModel, boolean cacheEnabled, @Nullable SecondLevelCache<ID, T> l2Cache, @Nullable ReadThroughCache<ID, T> readThroughCache) {
        this.cache = cache;
        this.objectModel = objectModel;
        this.cacheEnabled = cacheEnabled;
        this.l2Cache = l2Cache;
        this.readThroughCache = readThroughCache;
    }

    public List<T> insertToCache(String query, List<T> result) {
        if (cache != null) {
            cache.insert(query, result, objectModel::getId);
        }
        return result;
    }

    public List<T> fetch(String query) {
        return cache == null ? null : cache.fetch(query);
    }

    public void clear() {
        if (cache != null) {
            cache.clear();
        }
    }

    public void invalidateEntity(TransactionResult<Boolean> result, ID id) {
        if (result.isSuccess()) {
            // Invalidate L2 cache for this entity
            try {
                if (cacheEnabled) {
                    //noinspection DataFlowIssue
                    l2Cache.invalidate(id);
                    //noinspection DataFlowIssue
                    readThroughCache.invalidate(id);
                }
                Logging.deepInfo(() -> "Invalidated caches for entity ID: " + id);
            } catch (Exception e) {
                Logging.error("Failed to invalidate cache: " + e.getMessage());
            }
        }
    }

    public Map<ID, T> addResultAndAddToCache(List<T> ts, Map<ID, T> result) {
        for (T t : ts) {
            ID id = this.objectModel.getId(t);
            result.put(id, t);
            if (cacheEnabled) {
                //noinspection DataFlowIssue
                l2Cache.put(id, t);
                //noinspection DataFlowIssue
                readThroughCache.put(id, t);
            }
        }
        return result;
    }
}
