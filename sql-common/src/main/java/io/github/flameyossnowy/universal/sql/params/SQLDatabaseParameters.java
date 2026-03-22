package io.github.flameyossnowy.universal.sql.params;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Primitives;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.query.AggregationQueryParser;
import io.github.flameyossnowy.universal.sql.internals.query.ParameterizedSql;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps named parameters to JDBC positional indices using the ordered
 * {@link ParameterizedSql#parameterNames()} list produced by the SQL builders.
 */
@SuppressWarnings("unchecked")
public class SQLDatabaseParameters implements DatabaseParameters {

    private final PreparedStatement statement;
    private final TypeResolverRegistry typeRegistry;
    private final CollectionHandler collectionHandler;
    private final RepositoryModel<?, ?> repositoryInformation;
    private final boolean supportsArrays;

    /**
     * name -> 1-based JDBC parameter index.
     * Built once from the {@link ParameterizedSql} handed in by the caller.
     */
    private final Map<String, Integer> nameToIndexMap;

    /**
     * Tracks the next free index for parameters that are registered lazily via
     * {@link #setNull(String, Class)} (e.g. collection / relationship binders
     * that append parameters after the main SQL was constructed).
     */
    private int nextDynamicIndex;

    public SQLDatabaseParameters(
        PreparedStatement statement,
        TypeResolverRegistry typeRegistry,
        ParameterizedSql parameterizedSql,
        RepositoryModel<?, ?> information,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) {
        if (statement == null)    throw new IllegalArgumentException("PreparedStatement cannot be null");
        if (typeRegistry == null) throw new IllegalArgumentException("TypeResolverRegistry cannot be null");

        this.statement = statement;
        this.typeRegistry = typeRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;
        this.repositoryInformation = information;

        List<String> names = parameterizedSql.parameterNames();
        this.nameToIndexMap = new LinkedHashMap<>(Math.max(names.size() * 2, 8));

        for (int i = 0; i < names.size(); i++) {
            nameToIndexMap.putIfAbsent(names.get(i), i + 1);
        }

        this.nextDynamicIndex = names.size() + 1;
    }

    public SQLDatabaseParameters(
        PreparedStatement statement,
        TypeResolverRegistry typeRegistry,
        AggregationQueryParser.BoundSql parameterizedSql,
        RepositoryModel<?, ?> information,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) {
        if (statement == null)   throw new IllegalArgumentException("PreparedStatement cannot be null");
        if (typeRegistry == null) throw new IllegalArgumentException("TypeResolverRegistry cannot be null");

        this.statement = statement;
        this.typeRegistry = typeRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;
        this.repositoryInformation = information;

        List<String> names = parameterizedSql.paramNames();
        this.nameToIndexMap = new LinkedHashMap<>(Math.max(names.size() * 2, 8));

        for (int i = 0; i < names.size(); i++) {
            nameToIndexMap.putIfAbsent(names.get(i), i + 1);
        }

        this.nextDynamicIndex = names.size() + 1;
    }

    public SQLDatabaseParameters(
        PreparedStatement statement,
        TypeResolverRegistry typeRegistry,
        String sql,
        RepositoryModel<?, ?> information,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) {
        this(statement, typeRegistry, ParameterizedSql.of(sql), information, collectionHandler, supportsArrays);
    }

    private int getIndexForName(String key) {
        Integer mapped = nameToIndexMap.get(key);
        if (mapped != null) return mapped;

        if (repositoryInformation != null) {
            FieldModel<?> field = repositoryInformation.columnFieldByName(key);
            if (field != null) {
                mapped = nameToIndexMap.get(field.columnName());
                if (mapped != null) return mapped;
            }
        }

        throw new IllegalArgumentException("Unknown parameter: " + key);
    }

    @Override
    public CollectionHandler getCollectionHandler() { return collectionHandler; }

    @Override
    public String getAdapterType() { return "sql"; }

    @Override
    public boolean supportsArraysNatively() { return supportsArrays; }

    @Override
    public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        int idx = getIndexForName(name);

        if (value == null) {
            setNull(idx, type);
            return;
        }

        TypeResolver<Object> resolver = (TypeResolver<Object>) typeRegistry.resolve(Primitives.asWrapper(type));
        if (resolver != null) {
            resolver.insert(this, name, value);
            return;
        }

        setRaw(name, value, type);
    }

    @Override
    public <T> void setRaw(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        int idx = getIndexForName(name);

        if (value == null) {
            setNull(idx, type);
            return;
        }

        try {
            FieldModel<?> field = findFieldByNameOrColumnName(name);

            if (field != null && field.isJson()) {
                Object toBind = value;
                if (field.type().isInstance(value)) {
                    JsonCodec<Object> codec = typeRegistry.getJsonCodec(field.jsonCodec());
                    toBind = codec.serialize(value, (Class<Object>) field.type());
                }
                try {
                    statement.setObject(idx, String.valueOf(toBind), Types.OTHER);
                } catch (SQLException e) {
                    statement.setString(idx, String.valueOf(toBind));
                }
                return;
            }

            if      (type == byte.class    || type == Byte.class)      statement.setByte(idx, ((byte) value));
            else if (type == short.class   || type == Short.class)     statement.setShort(idx, ((short) value));
            else if (type == int.class     || type == Integer.class)   statement.setInt(idx, ((int) value));
            else if (type == long.class    || type == Long.class)      statement.setLong(idx, ((long) value));
            else if (type == float.class   || type == Float.class)     statement.setFloat(idx, ((float) value));
            else if (type == double.class  || type == Double.class)    statement.setDouble(idx, ((double) value));
            else if (type == boolean.class || type == Boolean.class)   statement.setBoolean(idx, (boolean) value);
            else if (type == char.class    || type == Character.class) statement.setString(idx, value.toString());
            else if (type == String.class)                             statement.setString(idx, (String) value);
            else                                                       statement.setObject(idx, value);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setNull(@NotNull String name, @NotNull Class<?> type) {
        // Register the name dynamically if it isn't in the map yet (e.g. a
        // collection/relationship binder adding extra params after construction).
        int index = nameToIndexMap.computeIfAbsent(name, n -> nextDynamicIndex++);
        setNull(index, type);
    }

    private void setNull(int index, @NotNull Class<?> type) {
        Class<?> lookup = Primitives.asWrapper(type);
        DataHandler<?> handler = typeRegistry.getHandler(lookup);
        int sqlType = handler != null ? handler.getSqlType() : Types.OTHER;

        try { statement.setNull(index, sqlType); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    private @Nullable FieldModel<?> findFieldByNameOrColumnName(@NotNull String name) {
        if (repositoryInformation == null) return null;
        FieldModel<?> byName = repositoryInformation.fieldByName(name);
        return byName != null ? byName : repositoryInformation.columnFieldByName(name);
    }

    @Override public int size() { return nextDynamicIndex - 1; }
    @Override public <T> @Nullable T get(int idx, @NotNull Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public boolean contains(@NotNull String name) { return nameToIndexMap.containsKey(name); }
    public PreparedStatement getStatement() { return statement; }
}