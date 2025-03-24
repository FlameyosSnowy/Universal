package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings({ "unchecked", "unused" })
public interface FetchedDataResult<T, ID> extends Iterable<T> {
    List<T> list();

    int size();

    boolean containsRelationship();

    FetchedDataResult<T, ID> sorted(Comparator<T> comparator);

    T first();

    T last();

    @Contract(value = " -> new", pure = true)
    static <T, ID> @NotNull FetchedDataResult<T, ID> empty() {
        return (FetchedDataResult<T, ID>) EmptyFetchedDataResult.INSTANCE;
    }

    @Contract("_ -> new")
    static <T, ID> @NotNull FetchedDataResult<T, ID> of(T data) {
        return data == null ? empty() : new SingletonFetchedDataResult<>(data);
    }

    @Contract("_ -> new")
    static <T, ID> @NotNull FetchedDataResult<T, ID> of(List<T> data) {
        return data == null || data.isEmpty() ? empty() : new ListFetchedDataResult<>(data);
    }

    boolean isEmpty();

    record SingletonFetchedDataResult<T, ID>(T d) implements FetchedDataResult<T, ID> {
        @Contract(value = " -> new", pure = true)
        @Override
        public @NotNull @Unmodifiable List<T> list() {
            return List.of(d);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean containsRelationship() {
            return false;
        }

        @Override
        public FetchedDataResult<T, ID> sorted(Comparator<T> comparator) {
            return this;
        }

        @Override
        public T first() {
            return d;
        }

        @Override
        public T last() {
            return d;
        }

        @Override
        public boolean isEmpty() {
            return d == null;
        }

        @Override
        public @NotNull Iterator<T> iterator() {
            return new Iterator<>() {
                boolean iterated = false;

                @Override
                public boolean hasNext() {
                    return !iterated;
                }

                @Override
                public T next() {
                    iterated = true;
                    return d;
                }
            };
        }
    }

    record ListFetchedDataResult<T, ID>(List<T> list) implements FetchedDataResult<T, ID> {
        @Override
        public int size() {
            return list.size();
        }

        @Override
        public boolean containsRelationship() {
            return false;
        }

        @Override
        public FetchedDataResult<T, ID> sorted(Comparator<T> comparator) {
            list.sort(comparator);
            return this;
        }

        @Override
        public T first() {
            return list.get(0);
        }

        @Override
        public T last() {
            return list.get(list.size() - 1);
        }

        @Override
        public boolean isEmpty() {
            return list.isEmpty();
        }

        @Override
        public @NotNull Iterator<T> iterator() {
            return list.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            int size = list.size();
            for (int i = 0; i < size; i++) action.accept(list.get(i));
        }

        @Override
        public String toString() {
            return list.toString();
        }

        @Override
        public int hashCode() {
            return list.hashCode();
        }
    }

    class EmptyFetchedDataResult<T, ID> implements FetchedDataResult<T, ID> {
        private static final EmptyFetchedDataResult<?, ?> INSTANCE = new EmptyFetchedDataResult<>();

        @Override
        public List<T> list() {
            return List.of();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean containsRelationship() {
            return false;
        }

        @Override
        public FetchedDataResult<T, ID> sorted(Comparator<T> valueComparator) {
            return this;
        }

        @Override
        public T first() {
            return null;
        }

        @Override
        public T last() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public @NotNull Iterator<T> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {}

        @Override
        public String toString() {
            return "[]";
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
