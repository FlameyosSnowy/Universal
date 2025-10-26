package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFRUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLFUCache;
import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Partitioned cache that reduces contention in multi-threaded environments.
 * Distributes entries across multiple partitions based on key hash.
 * Use this when you need to scale your cache beyond the number of available JVM threads.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
@SuppressWarnings("unused")
public class PartitionedCache<K, V> {
    private final Map<K, V>[] partitions;
    private final int partitionCount;
    private final CacheStatistics statistics = new CacheStatistics();
    
    @SuppressWarnings("unchecked")
    public PartitionedCache(int partitionCount, int sizePerPartition, CacheAlgorithmType type) {
        this.partitionCount = partitionCount;
        this.partitions = new Map[partitionCount];
        
        for (int i = 0; i < partitionCount; i++) {
            partitions[i] = switch (type) {
                case LEAST_RECENTLY_USED -> new ConcurrentLRUCache<>(sizePerPartition);
                case LEAST_FREQUENTLY_USED -> new ConcurrentLFUCache<>(sizePerPartition);
                case LEAST_FREQ_AND_RECENTLY_USED -> new ConcurrentLFRUCache<>(sizePerPartition);
                case NONE -> new ConcurrentHashMap<>(sizePerPartition);
            };
        }
    }
    
    /**
     * Gets the partition for a given key.
     */
    private Map<K, V> getPartition(K key) {
        int hash = key == null ? 0 : key.hashCode();
        // Use power-of-2 partitionCount for faster masking than modulo
        int index = hash & (partitionCount - 1);
        return partitions[index];
    }


    /**
     * Gets a value from the cache.
     * 
     * @param key the key
     * @return the value, or null if not found
     */
    public V get(K key) {
        V value = getPartition(key).get(key);
        if (value != null) {
            statistics.recordHit();
        } else {
            statistics.recordMiss(0);
        }
        return value;
    }
    
    /**
     * Puts a value into the cache.
     * 
     * @param key the key
     * @param value the value
     */
    public void put(K key, V value) {
        if (value != null) {
            getPartition(key).put(key, value);
            statistics.recordPut();
        }
    }
    
    /**
     * Removes a value from the cache.
     * 
     * @param key the key
     * @return the removed value, or null if not found
     */
    public V remove(K key) {
        V removed = getPartition(key).remove(key);
        if (removed != null) {
            statistics.recordEviction();
        }
        return removed;
    }
    
    /**
     * Clears all partitions.
     */
    public void clear() {
        int totalSize = 0;
        for (Map<K, V> partition : partitions) {
            totalSize += partition.size();
            partition.clear();
        }
        for (int i = 0; i < totalSize; i++) {
            statistics.recordEviction();
        }
    }
    
    /**
     * Gets the total size across all partitions.
     */
    public int size() {
        int total = 0;
        for (Map<K, V> partition : partitions) {
            total += partition.size();
        }
        return total;
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
