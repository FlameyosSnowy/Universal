package io.github.flameyossnowy.universal.microservices.network;

import io.github.flameyossnowy.universal.api.cache.CacheMetrics;
import io.github.flameyossnowy.universal.api.cache.CacheStatistics;
import io.github.flameyossnowy.universal.api.cache.SessionCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for network sessions.
 *
 * @param <ID> The ID type
 * @param <T> The entity type
 */
public class NetworkSessionCache<ID, T> implements SessionCache<ID, T> {
    private final ConcurrentHashMap<ID, T> cache = new ConcurrentHashMap<>();

    @Override
    public Map<ID, T> getInternalCache() {
        return cache;
    }

    @Override
    public T get(ID id) {
        return cache.get(id);
    }

    @Override
    public T put(ID id, T entity) {
        return cache.put(id, entity);
    }

    @Override
    public T remove(ID id) {
        return cache.remove(id);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public CacheStatistics getStatistics() {
        return CacheStatistics.empty();
    }

    @Override
    public CacheMetrics getMetrics() {
        return CacheMetrics.empty();
    }
}
