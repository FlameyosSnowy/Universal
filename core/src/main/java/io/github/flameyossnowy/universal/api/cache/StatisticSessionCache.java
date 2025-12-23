package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticSessionCache<ID, T> implements SessionCache<ID, T> {
    private final Map<ID, T> internalCache = new ConcurrentLRUCache<>(1024 * 5);
    private final CacheStatistics statistics = new CacheStatistics();

    @Override
    public Map<ID, T> getInternalCache() {
        return internalCache;
    }

    @Override
    public T get(ID id) {
        long l = System.currentTimeMillis();
        T t = internalCache.get(id);
        long duration = System.currentTimeMillis() - l;
        if (t != null) {
            statistics.recordHit();
        } else {
            statistics.recordMiss(duration);
        }
        return t;
    }

    @Override
    public T put(ID id, T value) {
        statistics.recordPut();
        return internalCache.put(id, value);
    }

    @Override
    public T remove(ID id) {
        statistics.recordEviction();
        return internalCache.remove(id);
    }

    @Override
    public void clear() {
        int size = internalCache.size();
        internalCache.clear();
        for (int i = 0; i < size; i++) {
            statistics.recordEviction();
        }
    }

    @Override
    public int size() {
        return internalCache.size();
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }

    @Override
    public CacheMetrics getMetrics() {
        return statistics.getMetrics();
    }
}
