package io.github.flameyossnowy.universal.api.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PrimitiveRegistry {
    private final Map<Class<?>, PrimitiveHandler> map = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, PrimitiveHandler handler) {
        map.put(type, handler);
    }

    public PrimitiveHandler get(Class<?> type) {
        return map.get(type);
    }
}