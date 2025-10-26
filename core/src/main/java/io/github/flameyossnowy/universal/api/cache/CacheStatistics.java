package io.github.flameyossnowy.universal.api.cache;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks cache performance metrics including hits, misses, evictions, and load times.
 * Thread-safe and designed for high-concurrency environments.
 */
public class CacheStatistics {
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final LongAdder totalLoadTime = new LongAdder();
    private final AtomicLong puts = new AtomicLong();
    
    /**
     * Records a cache hit.
     */
    public void recordHit() {
        hits.incrementAndGet();
    }
    
    /**
     * Records a cache miss with the time taken to load the value.
     * 
     * @param loadTimeMs the time taken to load the value in milliseconds
     */
    public void recordMiss(long loadTimeMs) {
        misses.incrementAndGet();
        totalLoadTime.add(loadTimeMs);
    }
    
    /**
     * Records a cache eviction.
     */
    public void recordEviction() {
        evictions.incrementAndGet();
    }
    
    /**
     * Records a cache put operation.
     */
    public void recordPut() {
        puts.incrementAndGet();
    }
    
    /**
     * Gets the total number of cache hits.
     */
    public long getHits() {
        return hits.get();
    }
    
    /**
     * Gets the total number of cache misses.
     */
    public long getMisses() {
        return misses.get();
    }
    
    /**
     * Gets the total number of cache evictions.
     */
    public long getEvictions() {
        return evictions.get();
    }
    
    /**
     * Gets the total number of put operations.
     */
    public long getPuts() {
        return puts.get();
    }
    
    /**
     * Calculates the cache hit rate as a percentage (0.0 to 1.0).
     */
    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }
    
    /**
     * Calculates the average load time for cache misses in milliseconds.
     */
    public double getAverageLoadTime() {
        long missCount = misses.get();
        return missCount == 0 ? 0.0 : (double) totalLoadTime.sum() / missCount;
    }
    
    /**
     * Gets a snapshot of all cache metrics.
     */
    public CacheMetrics getMetrics() {
        return new CacheMetrics(
            hits.get(),
            misses.get(),
            evictions.get(),
            puts.get(),
            getHitRate(),
            getAverageLoadTime()
        );
    }
    
    /**
     * Resets all statistics to zero.
     */
    public void reset() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
        puts.set(0);
        totalLoadTime.reset();
    }
    
    @Override
    public String toString() {
        return String.format(
            "CacheStatistics{hits=%d, misses=%d, hitRate=%.2f%%, avgLoadTime=%.2fms, evictions=%d}",
            hits.get(), misses.get(), getHitRate() * 100, getAverageLoadTime(), evictions.get()
        );
    }
}
