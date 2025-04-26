package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.cache.SessionCache;

import java.util.Map;

public class SQLSessionCache<ID, T> implements SessionCache<ID, T> {
    @Override
    public Map<ID, T> getInternalCache() {
        return Map.of();
    }

    @Override
    public T get(ID id) {
        return null;
    }

    @Override
    public T put(ID id, T value) {
        return null;
    }

    @Override
    public T remove(ID id) {
        return null;
    }

    @Override
    public void clear() {

    }
}
