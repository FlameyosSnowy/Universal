package io.github.flameyossnowy.universal.mongodb;

import io.github.flameyossnowy.universal.api.cache.SessionCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MongoSessionCache<ID, T> implements SessionCache<ID, T> {
    private final Map<ID, T> internalCache = new ConcurrentHashMap<>(16);

    @Override
    public Map<ID, T> getInternalCache() {
        return internalCache;
    }

    @Override
    public T get(ID id) {
        return internalCache.get(id);
    }

    @Override
    public T put(ID id, T value) {
        return internalCache.put(id, value);
    }

    @Override
    public T remove(ID id) {
        return internalCache.remove(id);
    }

    @Override
    public void clear() {
        internalCache.clear();
    }
}
