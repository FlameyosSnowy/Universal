package io.github.flameyossnowy.universal.mongodb;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A MongoDB-specific result cache that maps normalized filter documents to results.
 * Automatically clears affected caches when documents are updated or removed.
 */
public final class MongoResultCache<T, ID> {
    private final Map<Document, List<T>> cache;

    public MongoResultCache(int maxSize, @NotNull CacheAlgorithmType type) {
        this.cache = switch (type) {
            case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(maxSize);
            case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(maxSize);
            case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(maxSize);
            case NONE -> new ConcurrentHashMap<>(maxSize);
        };
    }

    public List<T> fetch(@NotNull Document filter) {
        return cache.get(filter);
    }

    public void insert(@NotNull Document filter, List<T> value) {
        cache.put(filter, value);
    }

    public void clear(@NotNull Document filter) {
        cache.remove(filter);
    }

    public void clear() {
        cache.clear();
    }
}
