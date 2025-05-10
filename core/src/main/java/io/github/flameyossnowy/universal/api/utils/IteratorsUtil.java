package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class IteratorsUtil {
    // array iterator

    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull Iterator<T> arrayIterator(T[] array) {
        return new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < array.length;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return array[index++];
            }
        };
    }

    // array list iterator

    public static <T> ListIterator<T> arrayListIterator(T[] array, int i) {
        return new ListIterator<>() {
            int index = i;

            @Override
            public boolean hasNext() {
                return index < array.length;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return array[index++];
            }

            @Override
            public boolean hasPrevious() {
                return index > 0;
            }

            @Override
            public T previous() {
                if (!hasPrevious()) throw new NoSuchElementException();
                return array[--index];
            }

            @Override
            public int nextIndex() {
                return index;
            }

            @Override
            public int previousIndex() {
                return index - 1;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(T t) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
