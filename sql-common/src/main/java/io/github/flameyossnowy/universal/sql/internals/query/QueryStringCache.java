package io.github.flameyossnowy.universal.sql.internals.query;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class QueryStringCache {
    private final Map<String, String> cache;

    public QueryStringCache(int initialCapacity) {
        this.cache = new ConcurrentHashMap<>(initialCapacity);
    }

    public String get(String key) {
        return cache.get(key);
    }

    public String computeIfAbsent(String key, java.util.function.Function<String, String> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }
}
