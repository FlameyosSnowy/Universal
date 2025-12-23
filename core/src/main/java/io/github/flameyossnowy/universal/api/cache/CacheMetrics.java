package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of cache performance metrics.
 * 
 * @param hits the total number of cache hits
 * @param misses the total number of cache misses
 * @param evictions the total number of cache evictions
 * @param puts the total number of put operations
 * @param hitRate the cache hit rate (0.0 to 1.0)
 * @param averageLoadTimeMs the average time to load a value on cache miss
 */
public record CacheMetrics(
    long hits,
    long misses,
    long evictions,
    long puts,
    double hitRate,
    double averageLoadTimeMs,
    long opsPerMinute
) {
    public static CacheMetrics empty() {
        return new CacheMetrics(0, 0, 0, 0, 0.0, 0.0, 0);
    }

    public long totalRequests() {
        return hits + misses;
    }
    
    public double missRate() {
        return 1.0 - hitRate;
    }
    
    @Override
    public @NotNull String toString() {
        return String.format(
            "CacheMetrics{requests=%d, hits=%d, misses=%d, hitRate=%.2f%%, avgLoadTime=%.2fms, evictions=%d}",
            totalRequests(), hits, misses, hitRate * 100, averageLoadTimeMs, evictions
        );
    }
}
