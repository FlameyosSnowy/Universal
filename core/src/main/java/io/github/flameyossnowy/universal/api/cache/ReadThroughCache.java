package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Read-through cache that automatically loads values on cache miss.
 * Provides transparent caching with built-in statistics.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class ReadThroughCache<K, V> {
    private final Map<K, V> cache;
    private final Function<K, V> loader;
    private final Function<List<K>, Map<K, V>> batchLoader;
    private final CacheStatistics statistics = new CacheStatistics();
    
    public ReadThroughCache(int maxSize, CacheAlgorithmType type, Function<K, V> loader) {
        this(maxSize, type, loader, null);
    }
    
    public ReadThroughCache(
        int maxSize,
        CacheAlgorithmType type,
        Function<K, V> loader,
        Function<List<K>, Map<K, V>> batchLoader
    ) {
        this.loader = loader;
        this.batchLoader = batchLoader;
        this.cache = switch (type) {
            case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(maxSize);
            case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(maxSize);
            case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(maxSize);
            case NONE -> new ConcurrentHashMap<>(maxSize);
        };
    }
    
    /**
     * Gets a value from the cache, loading it if necessary.
     * 
     * @param key the key
     * @return the value
     */
    public V get(K key) {
        V cached = cache.get(key);
        if (cached != null) {
            statistics.recordHit();
            return cached;
        }
        
        long start = System.currentTimeMillis();
        V loaded = loader.apply(key);
        long duration = System.currentTimeMillis() - start;
        
        statistics.recordMiss(duration);
        if (loaded != null) {
            cache.put(key, loaded);
            statistics.recordPut();
        }
        return loaded;
    }
    
    /**
     * Gets multiple values from the cache, batch loading missing ones.
     * 
     * @param keys the keys to fetch
     * @return map of key to value
     */
    public Map<K, V> getAll(List<K> keys) {
        Map<K, V> result = new HashMap<>();
        List<K> toLoad = new ArrayList<>();
        
        // Check cache first
        for (K key : keys) {
            V cached = cache.get(key);
            if (cached != null) {
                result.put(key, cached);
                statistics.recordHit();
            } else {
                toLoad.add(key);
            }
        }
        
        // Batch load missing
        if (!toLoad.isEmpty()) {
            long start = System.currentTimeMillis();
            Map<K, V> loaded;
            
            if (batchLoader != null) {
                loaded = batchLoader.apply(toLoad);
            } else {
                // Fallback to individual loading
                loaded = new HashMap<>();
                for (K key : toLoad) {
                    V value = loader.apply(key);
                    if (value != null) {
                        loaded.put(key, value);
                    }
                }
            }
            
            long duration = System.currentTimeMillis() - start;
            statistics.recordMiss(duration);
            
            result.putAll(loaded);
            cache.putAll(loaded);
            for (int i = 0; i < loaded.size(); i++) {
                statistics.recordPut();
            }
        }
        
        return result;
    }
    
    /**
     * Puts a value into the cache.
     * 
     * @param key the key
     * @param value the value
     */
    public void put(K key, V value) {
        if (value != null) {
            cache.put(key, value);
            statistics.recordPut();
        }
    }
    
    /**
     * Invalidates a specific key.
     * 
     * @param key the key to invalidate
     */
    public void invalidate(K key) {
        if (cache.remove(key) != null) {
            statistics.recordEviction();
        }
    }
    
    /**
     * Clears all entries from the cache.
     */
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
}
