package io.github.flameyossnow.universal.cassandra.objects;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CassandraDatabaseParameters implements DatabaseParameters {
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final List<String> parameterOrder = new ArrayList<>();

    @Override
    public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        if (!parameterOrder.contains(name)) {
            parameterOrder.add(name);
        }
        
        parameters.put(name, value);
    }

    @Override
    public void setNull(@NotNull String name, @NotNull Class<?> type) {
        set(name, null, type);
    }

    @Override
    public int size() {
        return parameterOrder.size();
    }

    @Override
    public <T> @Nullable T get(int index, @NotNull Class<T> type) {
        if (index < 1 || index > parameterOrder.size()) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        
        String name = parameterOrder.get(index - 1);
        return get(name, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) {
        return (T) parameters.get(name);
    }

    @Override
    public boolean contains(@NotNull String name) {
        return parameters.containsKey(name);
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Object[] getValues() {
        return parameters.values().toArray();
    }
}
