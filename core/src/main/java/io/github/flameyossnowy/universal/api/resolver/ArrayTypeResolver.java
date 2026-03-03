package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;

public record ArrayTypeResolver<T>(Class<T[]> arrayType, Class<T> componentType) implements TypeResolver<T[]> {

    @Override
    public Class<T[]> getType() {
        return arrayType;
    }

    @Override
    public Class<Object> getDatabaseType() {
        return Object.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T @Nullable [] resolve(DatabaseResult result, String columnName) {
        Object array = result.get(columnName, Object.class);
        switch (array) {
            case null -> {
                return null;
            }
            case Object[] ignored -> {
                return (T[]) array;
            }
            case java.sql.Array array1 -> {
                try {
                    return (T[]) array1.getArray();
                } catch (Exception e) {
                    throw new RuntimeException("Error getting array from result set", e);
                }
            }
            default -> {
            }
        }
        return (T[]) Array.newInstance(componentType, 0);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, T[] value) {
        parameters.set(index, value, Object.class);
    }
}
