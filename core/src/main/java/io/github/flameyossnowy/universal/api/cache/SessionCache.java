package io.github.flameyossnowy.universal.api.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Caching of a session object, may be used for globally (independent of a session) caching.
 * <p>
 * If the cache is implemented by a developer, it must be thread-safe and support concurrent access.
 * @param <ID>
 * @param <T>
 */
public interface SessionCache<ID, T> {
    Map<ID, T> getInternalCache();

    T get(ID id);

    T put(ID id, T value);

    default T putIfAbsent(ID id, T value) {
        T existing = get(id);
        if (existing == null) {
            put(id, value);
        }
        return existing;
    }

    default T replace(ID id, T value) {
        T existing = get(id);
        if (existing != null) {
            put(id, value);
        }
        return existing;
    }

    default T getOrDefault(ID id, T defaultValue) {
        T value = get(id);
        return value != null ? value : defaultValue;
    }

    T remove(ID id);

    void clear();

    default T computeIfAbsent(ID id, Function<? super ID, ? extends T> mappingFunction) {
        T value = get(id);
        if (value == null) {
            value = mappingFunction.apply(id);
            put(id, value);
        }
        return value;
    }

    default T computeIfPresent(ID id, BiFunction<? super ID, ? super T, ? extends T> remappingFunction) {
        T value = get(id);
        if (value != null) {
            value = remappingFunction.apply(id, value);
            put(id, value);
        }
        return value;
    }

    default T compute(ID id, BiFunction<? super ID, ? super T, ? extends T> remappingFunction) {
        T value = remappingFunction.apply(id, get(id));
        put(id, value);
        return value;
    }
}
