package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("FieldNotUsedInToString")
public class LazyHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {
    private Map<K, V> map;
    private final Supplier<Map<K, V>> supplier;

    public LazyHashMap(Supplier<Map<K, V>> supplier) {
        this.supplier = supplier;
    }

    protected Map<K, V> load() {
        return map == null ? (map = supplier.get()) : map;
    }

    @Override
    public int size() {
        return load().size();
    }

    @Override
    public boolean isEmpty() {
        return load().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return load().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return load().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return load().get(key);
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        return load().put(key, value);
    }

    @Override
    public V remove(Object key) {
        return load().remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        load().putAll(m);
    }

    @Override
    public void clear() {
        load().clear();
    }

    @Override
    public @NotNull Set<K> keySet() {
        return load().keySet();
    }

    @Override
    public @NotNull Collection<V> values() {
        return load().values();
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        return load().entrySet();
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return load().getOrDefault(key, defaultValue);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return load().putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return load().remove(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return load().replace(key, oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        return load().replace(key, value);
    }

    @Override
    public String toString() {
        return load().toString();
    }

    @Override
    public int hashCode() {
        return load().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LazyHashMap<?, ?> that)) {
            if (!(o instanceof Map)) return false;
            return load().equals(o);
        }
        if (this == that) return true;
        return Objects.equals(load(), that.load());
    }
}