package io.github.flameyossnowy.universal.sql.params;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL implementation of DatabaseParameters that wraps a JDBC PreparedStatement
 * and uses DataHandlers for type conversion.
 * <p>
 * Supports both index-based and name-based parameters.
 */
@SuppressWarnings("unchecked")
public class SQLDatabaseParameters implements DatabaseParameters {
    private final PreparedStatement statement;
    private final TypeResolverRegistry typeRegistry;
    private int parameterIndex = 1;
    private final Map<String, Integer> nameToIndexMap = new LinkedHashMap<>();

    /**
     * Creates a new SQLDatabaseParameters instance with a custom TypeResolverRegistry.
     *
     * @param statement the prepared statement to wrap
     * @param typeRegistry the type registry to use for type resolution
     */
    public SQLDatabaseParameters(PreparedStatement statement, TypeResolverRegistry typeRegistry) {
        if (statement == null) throw new IllegalArgumentException("PreparedStatement cannot be null");
        if (typeRegistry == null) throw new IllegalArgumentException("TypeResolverRegistry cannot be null");
        this.statement = statement;
        this.typeRegistry = typeRegistry;
    }

    @Override
    public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        int index = nameToIndexMap.computeIfAbsent(name, n -> parameterIndex++);
        if (value == null) {
            setNull(index, type);
            return;
        }

        TypeResolver<T> handler = (TypeResolver<T>) typeRegistry.getResolver(type);
        if (handler != null) {
            handler.insert(this, name, value);
            return;
        }

        try {
            statement.setObject(index, value);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting parameter at index " + index, e);
        }
    }

    private void setNull(int index, @NotNull Class<?> type) {
        DataHandler<?> handler = typeRegistry.getHandler(type);
        int sqlType = handler != null ? handler.getSqlType() : Types.OTHER;

        try {
            statement.setNull(index, sqlType);
        } catch (SQLException e) {
            throw new RuntimeException("Error setting null parameter at index " + index, e);
        }
    }

    @Override
    public void setNull(@NotNull String name, @NotNull Class<?> type) {
        int index = nameToIndexMap.computeIfAbsent(name, n -> parameterIndex++);
        setNull(index, type);
    }

    @Override
    public int size() {
        return Math.max(parameterIndex - 1, nameToIndexMap.size());
    }

    @Override
    public <T> @Nullable T get(int index, @NotNull Class<T> type) {
        throw new UnsupportedOperationException("Cannot get positional parameters in SQL");
    }

    @Override
    public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) {
        throw new UnsupportedOperationException("Cannot get named parameters in SQL");
    }

    @Override
    public boolean contains(@NotNull String name) {
        return nameToIndexMap.containsKey(name);
    }
}