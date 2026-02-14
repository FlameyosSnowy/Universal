package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.List;

/**
 * Default cache warmer that loads the most recent entities.
 * 
 * @param <T> the entity type
 * @param <ID> the identifier type
 */
public class DefaultCacheWarmer<T, ID> implements CacheWarmer<T, ID> {
    private final int warmupSize;
    
    public DefaultCacheWarmer() {
        this(1000);
    }
    
    public DefaultCacheWarmer(int warmupSize) {
        this.warmupSize = warmupSize;
    }
    
    @Override
    public void warmCache(RepositoryAdapter<T, ID, ?> adapter) {
        try {
            Logging.info(() -> "Warming cache for " + adapter.getElementType().getSimpleName() +
                        " (loading " + warmupSize + " entities)");
            
            long start = System.currentTimeMillis();
            List<T> entities = adapter.find(Query.select().limit(warmupSize).build());
            long duration = System.currentTimeMillis() - start;
            Logging.info(() -> "Cache warmed with " + entities.size() + " entities in " + duration + "ms");
            
        } catch (Exception e) {
            Logging.error("Failed to warm cache: " + e.getMessage());
        }
    }
    
    @Override
    public long getEstimatedWarmupTime() {
        return warmupSize * 2L; // Rough estimate: 2ms per entity
    }
}
