package io.github.flameyossnowy.universal.sql.internals.query;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class QueryStringCache {
    private final Map<String, ParameterizedSql> cache;

    public QueryStringCache(int initialCapacity) {
        this.cache = new ConcurrentHashMap<>(initialCapacity);
    }

    public ParameterizedSql get(String key) {
        return cache.get(key);
    }

    public ParameterizedSql computeIfAbsent(String key, java.util.function.Function<String, ParameterizedSql> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    public void put(String key, ParameterizedSql value) {
        cache.put(key, value);
    }
}
