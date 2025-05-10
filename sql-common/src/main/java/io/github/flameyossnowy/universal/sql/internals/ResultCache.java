package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A generic result cache that maps queries to cached results.
 * Automatically clears affected caches when data is modified.
 */
public final class ResultCache<T, ID> {
    private final Map<String, List<T>> cache;

    public ResultCache(int maxSize, @NotNull CacheAlgorithmType type) {
        this.cache = switch (type) {
            case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(maxSize);
            case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(maxSize);
            case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(maxSize);
            case NONE -> new ConcurrentHashMap<>(maxSize); // initial size.
        };
    }

    /**
     * Fetches the result for the given query from the cache.
     * <p>
     * If the query is not cached, this method will return {@code null}.
     *
     * @param query the query to fetch the result for
     * @return the cached result, or {@code null} if not cached
     */
    public List<T> fetch(String query) {
        return cache.get(query);
    }

    /**
     * Inserts a result into the cache.
     * <p>
     * If the query already exists in the cache, the old result is replaced with the new one.
     *
     * @param query the query to insert the result for
     * @param value the new result to insert
     */
    public void insert(String query, List<T> value) {
        cache.put(query, value);
    }

    /**
     * Clears all entries in the cache.
     * <p>
     * After calling this method, the cache will be empty and any previously cached
     * results will be removed.
     */
    public void clear() {
        cache.clear();
    }
}
