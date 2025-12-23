package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Second-level cache (L2) that stores entities with TTL support.
 * This cache survives write operations and provides entity-level caching.
 * 
 * @param <ID> the type of the entity identifier
 * @param <T> the type of the entity
 */
@SuppressWarnings("unused")
public class SecondLevelCache<ID, T> implements SessionCache<ID, T> {
    private final Map<ID, CachedEntity<T>> cache;
    private final long ttlMillis;
    private final CacheStatistics statistics = new CacheStatistics();
    
    public SecondLevelCache(int maxSize, long ttlMillis, CacheAlgorithmType type) {
        this.ttlMillis = ttlMillis;
        this.cache = switch (type) {
            case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(maxSize);
            case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(maxSize);
            case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(maxSize);
            case NONE -> new ConcurrentHashMap<>(maxSize);
        };
    }

    @Override
    public Map<ID, T> getInternalCache() {
        return this.cache.entrySet()
                .stream()
                .map((entry) -> Map.entry(entry.getKey(), entry.getValue().entity))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Gets an entity from the cache.
     * 
     * @param id the entity identifier
     * @return the cached entity, or null if not found or expired
     */
    public T get(ID id) {
        long start = System.currentTimeMillis();
        CachedEntity<T> cached = cache.get(id);
        long duration = System.currentTimeMillis() - start;
        if (cached == null) {
            statistics.recordMiss(duration);
            return null;
        }
        
        if (cached.isExpired()) {
            cache.remove(id);
            statistics.recordMiss(0);
            statistics.recordEviction();
            return null;
        }
        
        statistics.recordHit();
        return cached.entity;
    }
    
    /**
     * Puts an entity into the cache with TTL.
     *
     * @param id     the entity identifier
     * @param entity the entity to cache
     * @return the previous cached entity, or null if not found
     */
    @Override
    public T put(ID id, T entity) {
        if (entity == null) {
            return remove(id);
        }
        
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        statistics.recordPut();
        CachedEntity<T> old = cache.put(id, new CachedEntity<>(entity, expiresAt));
        if (old == null) {
            return null;
        }
        return old.entity;
    }

    @Override
    public T remove(ID id) {
        CachedEntity<T> remove = cache.remove(id);
        if (remove != null) {
            statistics.recordEviction();
            return remove.entity;
        }
        return null;
    }

    /**
     * Invalidates a specific entity in the cache.
     * 
     * @param id the entity identifier to invalidate
     */
    public void invalidate(ID id) {
        if (cache.remove(id) != null) {
            statistics.recordEviction();
        }
    }
    
    /**
     * Clears all entries from the cache.
     */
    @Override
    public void clear() {
        int size = cache.size();
        cache.clear();
        for (int i = 0; i < size; i++) {
            statistics.recordEviction();
        }
    }
    
    /**
     * Gets the current size of the cache.
     */
    @Override
    public int size() {
        return cache.size();
    }
    
    /**
     * Gets cache statistics.
     */
    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * Gets cache metrics snapshot.
     */
    @Override
    public CacheMetrics getMetrics() {
        return statistics.getMetrics();
    }
    
    /**
     * Cached entity wrapper with expiration time.
     */
    private record CachedEntity<T>(T entity, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
