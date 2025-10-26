package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;

/**
 * Interface for cache warming strategies.
 * Implementations can pre-load frequently accessed data into cache on startup.
 * 
 * @param <T> the entity type
 * @param <ID> the identifier type
 */
public interface CacheWarmer<T, ID> {
    /**
     * Warms the cache by pre-loading data.
     * 
     * @param adapter the repository adapter to warm
     */
    void warmCache(RepositoryAdapter<T, ID, ?> adapter);
    
    /**
     * Gets the estimated time to warm the cache in milliseconds.
     */
    default long getEstimatedWarmupTime() {
        return 1000; // 1 second default
    }
}
