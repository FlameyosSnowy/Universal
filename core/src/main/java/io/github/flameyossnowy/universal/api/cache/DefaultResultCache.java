package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Smart query result cache with selective invalidation.
 * Tracks which queries contain which entity IDs for efficient cache invalidation.
 * 
 * @param <Q> the query type
 * @param <T> the entity type
 * @param <ID> the entity identifier type
 */
public class DefaultResultCache<Q, T, ID> {
    private static final long DEFAULT_TTL_MILLIS = 120_000; // 2 minutes

    private final Map<Q, CacheEntry<T>> cache;
    private final Map<ID, Set<Q>> idToQueries = new ConcurrentHashMap<>();
    private final CacheStatistics statistics = new CacheStatistics();
    private final long ttlMillis;
    
    public DefaultResultCache(int maxSize, long ttlMillis, CacheAlgorithmType type) {
        this.ttlMillis = ttlMillis;
        this.cache = switch (type) {
            case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(maxSize);
            case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(maxSize);
            case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(maxSize);
            case NONE -> new ConcurrentHashMap<>(maxSize);
        };
    }

    public DefaultResultCache(int maxSize, CacheAlgorithmType type) {
        this(maxSize, DEFAULT_TTL_MILLIS, type);
    }
    
    /**
     * Fetches cached query results.
     * 
     * @param query the query to fetch results for
     * @return the cached results, or null if not found or expired
     */
    public List<T> fetch(Q query) {
        CacheEntry<T> entry = cache.get(query);
        if (entry == null) {
            statistics.recordMiss(0);
            return null;
        }
        
        if (entry.isExpired()) {
            cache.remove(query);
            statistics.recordMiss(0);
            statistics.recordEviction();
            return null;
        }
        
        statistics.recordHit();
        return entry.results;
    }
    
    /**
     * Inserts query results into the cache with ID tracking.
     * 
     * @param query the query
     * @param results the query results
     * @param idExtractor function to extract ID from entity
     */
    public void insert(Q query, List<T> results, Function<T, ID> idExtractor) {
        if (results == null || results.isEmpty()) {
            return;
        }
        
        long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        cache.put(query, new CacheEntry<>(results, expiresAt));
        statistics.recordPut();
        
        // Track which queries contain which IDs
        for (T entity : results) {
            try {
                ID id = idExtractor.apply(entity);
                if (id != null) {
                    idToQueries.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(query);
                }
            } catch (Exception e) {
                // Ignore extraction errors
            }
        }
    }
    
    /**
     * Invalidates all queries that contain the specified entity ID.
     * This is much more efficient than clearing the entire cache.
     * 
     * @param id the entity ID to invalidate
     */
    public void invalidate(ID id) {
        Set<Q> affectedQueries = idToQueries.remove(id);
        if (affectedQueries != null) {
            for (Q query : affectedQueries) {
                if (cache.remove(query) != null) {
                    statistics.recordEviction();
                }
            }
        }
    }
    
    /**
     * Invalidates multiple entity IDs at once.
     * 
     * @param ids the entity IDs to invalidate
     */
    public void invalidateAll(Collection<ID> ids) {
        for (ID id : ids) {
            invalidate(id);
        }
    }
    
    /**
     * Clears a specific query from the cache.
     * 
     * @param query the query to clear
     */
    public void clear(Q query) {
        if (cache.remove(query) != null) {
            statistics.recordEviction();
        }
    }
    
    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        idToQueries.clear();
        for (int i = 0; i < size; i++) {
            statistics.recordEviction();
        }
    }
    
    /**
     * Gets the current size of the cache.
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Gets cache statistics.
     */
    public CacheStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * Gets cache metrics snapshot.
     */
    public CacheMetrics getMetrics() {
        return statistics.getMetrics();
    }
    
    /**
     * Cache entry with expiration time.
     */
    private record CacheEntry<T>(List<T> results, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
