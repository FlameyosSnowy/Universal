package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * Optimized lazy list that caches size without loading all data.
 * Provides fast isEmpty() and size() checks using COUNT queries.
 * 
 * @param <T> the element type
 */
public class OptimizedLazyList<T> extends AbstractList<T> implements List<T> {
    private List<T> list;
    private Integer cachedSize;
    private final Supplier<List<T>> supplier;
    private final Supplier<Integer> sizeSupplier;
    
    public OptimizedLazyList(Supplier<List<T>> supplier, Supplier<Integer> sizeSupplier) {
        this.supplier = supplier;
        this.sizeSupplier = sizeSupplier;
    }
    
    /**
     * Gets the size without loading all data.
     * Uses a fast COUNT query if available.
     */
    @Override
    public int size() {
        if (list != null) return list.size();
        if (cachedSize != null) return cachedSize;
        
        // Execute COUNT query instead of loading all data
        cachedSize = sizeSupplier.get();
        return cachedSize;
    }
    
    /**
     * Checks if empty without loading all data.
     */
    @Override
    public boolean isEmpty() {
        if (list != null) return list.isEmpty();
        if (cachedSize != null) return cachedSize == 0;
        
        // Fast empty check
        cachedSize = sizeSupplier.get();
        return cachedSize == 0;
    }
    
    @Override
    public T get(int index) {
        return load().get(index);
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
        if (!(o instanceof OptimizedLazyList<?> that)) {
            if (!(o instanceof List)) return false;
            return load().equals(o);
        }
        if (this == that) return true;
        return Objects.equals(load(), that.load());
    }
    
    /**
     * Loads the list if not already loaded.
     */
    private List<T> load() {
        if (list == null) {
            list = supplier.get();
            cachedSize = list.size();
        }
        return list;
    }
    
    /**
     * Checks if the list has been loaded.
     */
    public boolean isLoaded() {
        return list != null;
    }
}
