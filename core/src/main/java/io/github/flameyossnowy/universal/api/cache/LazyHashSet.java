package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("FieldNotUsedInToString")
public class LazyHashSet<T> extends AbstractSet<T> implements Set<T> {
    private Set<T> set;
    private final Supplier<Set<T>> supplier;

    public LazyHashSet(Supplier<Set<T>> supplier) {
        this.supplier = supplier;
    }

    protected Set<T> load() {
        return set == null ? (set = supplier.get()) : set;
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
    public boolean contains(Object o) {
        return load().contains(o);
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return load().iterator();
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        return load().toArray();
    }

    @Override
    public @NotNull <D> D @NotNull [] toArray(@NotNull D @NotNull [] a) {
        return load().toArray(a);
    }

    @Override
    public boolean add(T e) {
        return load().add(e);
    }

    @Override
    public boolean remove(Object o) {
        return load().remove(o);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return load().containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        return load().addAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return load().retainAll(c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return load().removeAll(c);
    }

    @Override
    public void clear() {
        load().clear();
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
        if (!(o instanceof LazyHashSet<?> that)) {
            if (!(o instanceof Set)) return false;
            return load().equals(o);
        }
        if (this == that) return true;
        return Objects.equals(load(), that.load());
    }
}