package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A generic result cache that maps queries to cached results.
 * Automatically clears affected caches when data is modified.
 */
public final class ResultCache<T, ID> {
    private final Map<String, FetchedDataResult<T, ID>> cache;

    public ResultCache(int maxSize, @NotNull CacheAlgorithmType type) {
        this.cache = switch (type) {
            case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(maxSize);
            case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(maxSize);
            case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(maxSize);
        };
    }

    public FetchedDataResult<T, ID> fetch(String query) {
        return cache.get(query);
    }

    public void insert(String query, FetchedDataResult<T, ID> value) {
        cache.put(query, value);
    }

    public void clear(String query) {
        cache.remove(query);
    }

    public void clear() {
        cache.clear();
    }
}
