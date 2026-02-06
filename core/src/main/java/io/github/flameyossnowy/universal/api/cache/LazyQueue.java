package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("FieldNotUsedInToString")
public class LazyQueue<T> extends AbstractQueue<T> implements Queue<T> {

    private Queue<T> queue;
    private final Supplier<Queue<T>> supplier;

    public LazyQueue(@NotNull Supplier<Queue<T>> supplier) {
        this.supplier = supplier;
    }

    protected Queue<T> load() {
        return queue == null ? (queue = supplier.get()) : queue;
    }

    @Override
    public boolean offer(T t) {
        return load().offer(t);
    }

    @Override
    public T poll() {
        return load().poll();
    }

    @Override
    public T peek() {
        return load().peek();
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
    public Object @NotNull [] toArray() {
        return load().toArray();
    }

    @Override
    public <D> D @NotNull [] toArray(D @NotNull [] a) {
        return load().toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return load().remove(o);
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
        if (this == o) return true;
        if (o instanceof LazyQueue<?> that) {
            return Objects.equals(load(), that.load());
        }
        if (o instanceof Queue<?> q) {
            return load().equals(q);
        }
        return false;
    }
}
