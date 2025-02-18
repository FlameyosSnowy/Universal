package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.velocis.cache.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.ConcurrentLFUCache;

import java.util.*;

/**
 * A generic result cache that maps queries to cached results.
 * Automatically clears affected caches when data is modified.
 */
public final class ResultCache {
    private final Map<Query, Object> cache;

    public ResultCache(int maxSize, CacheAlgorithmType type) {
        this.cache = type == CacheAlgorithmType.LEAST_FREQUENTLY_USED
                ? new ConcurrentLFUCache<>(maxSize)
                : new ConcurrentLFRUCache<>(maxSize);
    }

    public <T> T get(Query query) {
        return (T) cache.get(query);
    }

    public <T> void refresh(Query query, T value) {
        cache.put(query, value);
    }

    public void clear(Query query) {
        cache.remove(query);
    }

    public void clear() {
        cache.clear();
    }
}
