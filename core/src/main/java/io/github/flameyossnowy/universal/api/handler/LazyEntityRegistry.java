package io.github.flameyossnowy.universal.api.handler;

import java.util.HashMap;
import java.util.Map;

public final class LazyEntityRegistry {

    private static final Map<LazyBatchKey, Map<Object, Object>> DATA =
        new HashMap<>();

    public static void attach(
        LazyBatchKey key,
        Object ownerId,
        Object entity
    ) {
        DATA
            .computeIfAbsent(key, ignored -> new HashMap<>())
            .put(ownerId, entity);
    }

    public static Object resolve(LazyBatchKey key, Object ownerId) {
        Map<Object, Object> map = DATA.get(key);
        return map == null ? null : map.get(ownerId);
    }
}
