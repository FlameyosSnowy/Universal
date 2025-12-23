package io.github.flameyossnowy.universal.api.cache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Cache with automatic prefetching based on access patterns.
 * Detects sequential access and prefetches next values.
 * 
 * @param <ID> the identifier type
 * @param <T> the entity type
 */
public class PrefetchCache<ID, T> implements SessionCache<ID, T> {
    private final Map<ID, T> cache = new ConcurrentHashMap<>();
    private final Queue<ID> accessHistory = new ConcurrentLinkedQueue<>();
    private final int prefetchThreshold;
    private final int prefetchSize;
    private final CacheStatistics statistics = new CacheStatistics();
    private final Function<List<ID>, Map<ID, T>> batchLoader;
    
    public PrefetchCache(int prefetchThreshold, int prefetchSize, Function<List<ID>, Map<ID, T>> batchLoader) {
        this.prefetchThreshold = prefetchThreshold;
        this.prefetchSize = prefetchSize;
        this.batchLoader = batchLoader;
    }

    @Override
    public Map<ID, T> getInternalCache() {
        return cache;
    }

    /**
     * Gets a value from the cache with automatic prefetching.
     * 
     * @param id the identifier
     * @return the value
     */
    public T get(ID id) {
        accessHistory.offer(id);
        if (accessHistory.size() > 100) {
            accessHistory.poll();
        }
        
        T cached = cache.get(id);
        if (cached != null) {
            statistics.recordHit();
            return cached;
        }
        
        long start = System.currentTimeMillis();
        
        if (shouldPrefetch()) {
            List<ID> toPrefetch = predictNextIds(id);
            Map<ID, T> loaded = batchLoader.apply(toPrefetch);
            cache.putAll(loaded);
            for (int i = 0; i < loaded.size(); i++) {
                statistics.recordPut();
            }
            
            long duration = System.currentTimeMillis() - start;
            statistics.recordMiss(duration);
            return loaded.get(id);
        }
        
        // Single load
        Map<ID, T> loaded = batchLoader.apply(List.of(id));
        cache.putAll(loaded);
        statistics.recordPut();
        
        long duration = System.currentTimeMillis() - start;
        statistics.recordMiss(duration);
        return loaded.get(id);
    }
    
    /**
     * Determines if prefetching should be triggered.
     */
    private boolean shouldPrefetch() {
        return accessHistory.size() >= prefetchThreshold;
    }
    
    /**
     * Predicts next IDs based on access pattern.
     */
    @SuppressWarnings("unchecked")
    private List<ID> predictNextIds(ID currentId) {
        List<ID> predicted = new ArrayList<>();
        predicted.add(currentId);
        
        // For numeric IDs, prefetch next N
        if (currentId instanceof Number num) {
            for (int i = 1; i <= prefetchSize; i++) {
                predicted.add((ID) (Long) (num.longValue() + i));
            }
        }
        
        return predicted;
    }
    
    /**
     * Puts a value into the cache.
     *
     * @param id    the identifier
     * @param value the value
     * @return
     */
    public T put(ID id, T value) {
        if (value != null) {
            statistics.recordPut();
            return cache.put(id, value);
        }
        return remove(id);
    }

    @Override
    public T remove(ID id) {
        if (id == null) {
            return null;
        }
        T remove = cache.remove(id);
        if (remove != null) {
            statistics.recordEviction();
        }
        return remove;
    }
    
    /**
     * Clears all entries from the cache.
     */
    @Override
    public void clear() {
        int size = cache.size();
        cache.clear();
        accessHistory.clear();
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
}
