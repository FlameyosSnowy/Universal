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
 * @param <ID> the id class
 * @param <T> the element class
 */
public interface SessionCache<ID, T> {
    /**
     * Returns the internal cache backing this session cache.
     * <p>
     * This may be used for introspection, or for other advanced operations.
     * @return the internal cache backing this session cache.
     */
    Map<ID, T> getInternalCache();

    /**
     * Returns the value associated with the given {@code id}.
     * <p>
     * If no mapping of the specified key exists, this method returns {@code null}.
     * @param id the key whose associated value is to be returned.
     * @return the value associated with the given {@code id}, or {@code null} if no mapping of the specified key exists.
     */
    T get(ID id);

    /**
     * Maps the specified {@code id} to the specified {@code value}.
     * <p>
     * If the cache previously contained a mapping for the key, the old
     * value is replaced by the specified {@code value}.
     * @param id key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the value associated with the given {@code id}, or {@code null} if no mapping of the specified key exists
     */
    T put(ID id, T value);


    /**
     * Maps the specified {@code id} to the specified {@code value} if the key is not already associated with a value.
     * <p>
     * If the cache does not contain a mapping for the key, the specified {@code value} is associated with the key.
     * Otherwise, no changes are made to the cache.
     *
     * @param id the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key if absent
     * @return the existing value associated with the key, or {@code null} if no mapping existed
     */
    default T putIfAbsent(ID id, T value) {
        T existing = get(id);
        if (existing == null) {
            put(id, value);
        }
        return existing;
    }

    /**
     * Replaces the entry for the specified {@code id} only if it is already present.
     * <p>
     * If the cache contains a mapping for the key, the old value is replaced by
     * the specified {@code value}.  Otherwise, no changes are made to the cache.
     * @param id the key with which the specified value is associated
     * @param value the value to be associated with the specified key
     * @return the previous value associated with the specified key, or {@code null} if there was no mapping for the key
     */
    default T replace(ID id, T value) {
        T existing = get(id);
        if (existing != null) {
            put(id, value);
        }
        return existing;
    }

    /**
     * Returns the value associated with the given {@code id} if it exists,
     * or the specified {@code defaultValue} otherwise.
     * <p>
     * If the cache contains a mapping for the key, the associated value is
     * returned.  Otherwise, the specified {@code defaultValue} is returned.
     * @param id the key whose associated value is to be returned
     * @param defaultValue the value to return if the key is not present
     * @return the value associated with the key, or the default value if none exists
     */
    default T getOrDefault(ID id, T defaultValue) {
        T value = get(id);
        return value != null ? value : defaultValue;
    }

    /**
     * Removes the mapping for the specified {@code id} if present.
     * <p>
     * If the cache contains a mapping for the key, the associated value is
     * returned and the entry is removed.  Otherwise, {@code null} is returned.
     * @param id the key whose mapping is to be removed
     * @return the value associated with the key, or {@code null} if no mapping existed
     */
    T remove(ID id);

    /**
     * Removes all mappings from the cache.
     * <p>
     * This method is used to clear the cache, discarding all stored values.
     * It is typically used when the cache is no longer valid, or when the cache
     * needs to be reset.
     */
    void clear();

    /**
     * Computes the value associated with the given {@code id} if it is not
     * already present, using the given {@code mappingFunction}.
     * <p>
     * If the cache contains a mapping for the key, the associated value is
     * returned.  Otherwise, the {@code mappingFunction} is invoked to compute
     * the associated value, which is then stored in the cache and returned.
     * @param id the key whose associated value is to be computed
     * @param mappingFunction the function to use to compute the associated value
     * @return the associated value, or the computed value if none exists
     */
    default T computeIfAbsent(ID id, Function<? super ID, ? extends T> mappingFunction) {
        T value = get(id);
        if (value == null) {
            value = mappingFunction.apply(id);
            put(id, value);
        }
        return value;
    }

    /**
     * Computes the value associated with the given {@code id} if it is present,
     * using the given {@code remappingFunction}.
     * <p>
     * If the cache contains a mapping for the key, the associated value is
     * passed to the {@code remappingFunction} to compute a new value, which is
     * then stored in the cache and returned.  Otherwise, {@code null} is
     * returned.
     * @param id the key whose associated value is to be computed
     * @param remappingFunction the function to use to compute the associated value
     * @return the associated value, or the computed value if none exists
     */
    default T computeIfPresent(ID id, BiFunction<? super ID, ? super T, ? extends T> remappingFunction) {
        T value = get(id);
        if (value != null) {
            value = remappingFunction.apply(id, value);
            put(id, value);
        }
        return value;
    }

    /**
     * Computes the value associated with the given {@code id} by applying the
     * given {@code remappingFunction} to the current value, or the default
     * value if no mapping exists.
     * <p>
     * If the cache contains a mapping for the key, the associated value is
     * passed to the remapping function.  Otherwise, the remapping function is
     * invoked with the default value of {@code null}.  The result of the
     * remapping function is then stored in the cache and returned.
     * @param id the key whose associated value is to be computed
     * @param remappingFunction the function to use to compute the associated value
     * @return the associated value, or the computed value if none exists
     **/
    default T compute(ID id, BiFunction<? super ID, ? super T, ? extends T> remappingFunction) {
        T value = remappingFunction.apply(id, get(id));
        put(id, value);
        return value;
    }

    int size();

    CacheStatistics getStatistics();

    CacheMetrics getMetrics();
}
