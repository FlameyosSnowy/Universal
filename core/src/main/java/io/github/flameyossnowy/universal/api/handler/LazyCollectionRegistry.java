package io.github.flameyossnowy.universal.api.handler;

import java.util.HashMap;
import java.util.Map;

public final class LazyCollectionRegistry {

    private static final Map<Key, Object> DATA = new HashMap<>();

    public static void register(
        LazyBatchKey key,
        long ownerId,
        Object proxy
    ) {
        DATA.put(new Key(key, ownerId), proxy);
    }

    @SuppressWarnings("unchecked")
    public static <T> T resolve(
        LazyBatchKey key,
        long ownerId
    ) {
        return (T) DATA.get(new Key(key, ownerId));
    }

    private record Key(LazyBatchKey k, long id) {}
}
