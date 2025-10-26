package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ImmutableList<T> implements List<T> {
    private final T[] array;

    @SuppressWarnings("unchecked")
    public ImmutableList(Object[] array) {
        this.array = (T[]) array;
    }

    @Override
    public int size() {
        return array.length;
    }

    @Override
    public boolean isEmpty() {
        return array.length == 0;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return IteratorsUtil.arrayIterator(array);
    }

    @Override
    public @NotNull Object @NotNull [] toArray() {
        return array;
    }

    @Override
    public @NotNull <T1> T1 @NotNull [] toArray(@NotNull T1 @NotNull [] t1s) {
        System.arraycopy(array, 0, t1s, 0, array.length);
        return t1s;
    }

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        for (Object o : collection) {
            if (!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int i, @NotNull Collection<? extends T> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(int i) {
        return this.array[i];
    }

    @Override
    public T set(int i, T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int i, T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        int length = array.length;
        for (int i = 0; i < length; i++) {
            if (Objects.equals(array[i], o))
                return i;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int length = array.length;
        for (int i = length - 1; i >= 0; i--) {
            if (Objects.equals(array[i], o))
                return i;
        }
        return -1;
    }

    @Override
    public @NotNull ListIterator<T> listIterator() {
        return IteratorsUtil.arrayListIterator(array, 0);
    }

    @Override
    public @NotNull ListIterator<T> listIterator(int i) {
        return IteratorsUtil.arrayListIterator(array, i);
    }

    @Override
    public @NotNull List<T> subList(int i, int i1) {
        return new SubList(array, i, i1, i1 - i);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (T t : array) {
            hash += Objects.hashCode(t);
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutableList<?> list)) {
            if (!(o instanceof List)) return false;
            return Arrays.equals(array, ((List<?>) o).toArray());
        }
        return Arrays.equals(array, list.array);
    }

    @Override
    public String toString() {
        return Arrays.toString(array);
    }

    // sublist class
    public class SubList extends ImmutableList<T> {
        private final int offset;
        private final int endOffset;
        private final int length;

        public SubList(T[] array, int offset, int endOffset, int length) {
            super(array);
            this.offset = offset;
            this.endOffset = endOffset;
            this.length = length;
        }

        @Override
        public int size() {
            return length;
        }

        @Override
        public T get(int i) {
            if (i < offset || i >= endOffset)
                throw new IndexOutOfBoundsException();
            return array[offset + i];
        }
    }
}
