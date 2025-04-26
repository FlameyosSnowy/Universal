package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("FieldNotUsedInToString")
public class LazyArrayList<T> extends AbstractList<T> implements List<T> {
    private List<T> list;
    private final Supplier<List<T>> supplier;

    public LazyArrayList(Supplier<List<T>> supplier) {
        this.supplier = supplier;
    }

    protected List<T> load() {
        return list == null ? (list = supplier.get()) : list;
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
    public boolean addAll(int index, @NotNull Collection<? extends T> c) {
        return load().addAll(index, c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return load().removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return load().retainAll(c);
    }

    @Override
    public void clear() {
        load().clear();
    }

    @Override
    public T get(int index) {
        return load().get(index);
    }

    @Override
    public T set(int index, T element) {
        return load().set(index, element);
    }

    @Override
    public void add(int index, T element) {
        load().add(index, element);
    }

    @Override
    public T remove(int index) {
        return load().remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return load().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return load().lastIndexOf(o);
    }

    @Override
    public @NotNull ListIterator<T> listIterator() {
        return load().listIterator();
    }

    @Override
    public @NotNull ListIterator<T> listIterator(int index) {
        return load().listIterator(index);
    }

    @Override
    public @NotNull List<T> subList(int fromIndex, int toIndex) {
        return load().subList(fromIndex, toIndex);
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
        if (!(o instanceof LazyArrayList<?> that)) {
            if (!(o instanceof List)) return false;
            return load().equals(o);
        }
        if (this == that) return true;
        return Objects.equals(load(), that.load());
    }
}
