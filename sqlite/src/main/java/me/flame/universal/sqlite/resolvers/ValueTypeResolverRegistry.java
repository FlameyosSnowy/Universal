package me.flame.universal.sqlite.resolvers;

import me.flame.universal.sqlite.resolvers.ValueTypeResolver;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, ValueTypeResolver> resolvers = new HashMap<>();

    private static final Map<Class<?>, String> ENCODED_TYPE_MAPPERS = Map.ofEntries(
            Map.entry(String.class, "TEXT"),
            Map.entry(Integer.class, "INT"),
            Map.entry(int.class, "INT"),
            Map.entry(Long.class, "BIGINT"),
            Map.entry(long.class, "BIGINT"),
            Map.entry(Double.class, "INT"),
            Map.entry(double.class, "INT"),
            Map.entry(Float.class, "INT"),
            Map.entry(float.class, "INT"),
            Map.entry(byte[].class, "BLOB"),
            Map.entry(Timestamp.class, ""),
            Map.entry(Time.class, ""),
            Map.entry(Date.class, "")
    );

    public ValueTypeResolverRegistry() {
        register(String.class,
                String.class,
                ResultSet::getString,
                (stmt, index, value) -> stmt.setString(index, (String) value));

        register(Integer.class,
                Integer.class,
                ResultSet::getInt,
                (stmt, index, value) -> stmt.setInt(index, (Integer) value));

        register(Long.class,
                Long.class,
                ResultSet::getLong,
                (stmt, index, value) -> stmt.setLong(index, (Long) value));

        register(Float.class,
                Float.class,
                ResultSet::getFloat,
                (stmt, index, value) -> stmt.setFloat(index, (Float) value));

        register(Double.class,
                Double.class,
                ResultSet::getFloat,
                (stmt, index, value) -> stmt.setDouble(index, (double) value));

        register(int.class,
                int.class,
                ResultSet::getInt,
                (stmt, index, value) -> stmt.setInt(index, (int) value));

        register(long.class,
                long.class,
                ResultSet::getLong,
                (stmt, index, value) -> stmt.setLong(index, (long) value));

        register(float.class,
                float.class,
                ResultSet::getFloat,
                (stmt, index, value) -> stmt.setFloat(index, (float) value));

        register(double.class,
                double.class,
                ResultSet::getDouble,
                (stmt, index, value) -> stmt.setDouble(index, (double) value));

        register(UUID.class,
                String.class,
                (stmt, index) -> {
                    String value = stmt.getString(index);
                    if (value == null || value.isEmpty()) return null;
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException e) {
                        throw new SQLException("Invalid UUID value: " + value, e);
                    }
                },
                (stmt, index, value) -> stmt.setString(index, value.toString())
        );

        register(Instant.class,
                String.class,
                (stmt, index) -> {
                    String value = stmt.getString(index);
                    if (value == null || value.isEmpty()) return null;
                    try {
                        return Instant.parse(value);
                    } catch (IllegalArgumentException e) {
                        throw new SQLException("Invalid UUID value: " + value, e);
                    }
                },
                (stmt, index, value) -> stmt.setString(index, value.toString()));

        register(Time.class,
                Time.class,
                ResultSet::getTime,
                (stmt, index, value) -> stmt.setTime(index, (Time) value));

        register(Date.class,
                Date.class,
                ResultSet::getDate,
                (stmt, index, value) -> stmt.setDate(index, (Date) value));

        register(Serializable.class,
                byte[].class,
                (stmt, index) -> {
                    byte[] array = stmt.getBytes(index);
                    if (array == null) return null;
                    try (ByteArrayInputStream byteIn = new ByteArrayInputStream(array);
                         ObjectInputStream objectIn = new ObjectInputStream(byteIn)) {
                        return objectIn.readObject();
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                },
                (stmt, index, value) -> {
                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                         ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
                        objectOut.writeObject(value);
                        byte[] array = byteOut.toByteArray();
                        stmt.setBytes(index, array);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public void register(Class<?> type, Class<?> encodedType, ResolverFactory resolver, InsertFactory insertInt) {
        resolvers.put(type, new DefaultValueTypeResolver(encodedType, resolver, insertInt));
    }

    public void register(Class<?> type, ValueTypeResolver resolver) {
        resolvers.put(type, resolver);
    }

    public ValueTypeResolver getResolver(Class<?> type) {
        ValueTypeResolver resolver = resolvers.get(type);
        if (resolver != null) return resolver;

        if (Serializable.class.isAssignableFrom(type)) {
            return resolvers.get(Serializable.class);
        }

        return null;
    }

    public String getType(@NotNull ValueTypeResolver resolver) {
        String type = ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
        }
        return type;
    }

    public String getType(Class<?> resolver) {
        ValueTypeResolver valueTypeResolver = this.getResolver(resolver);
        String type = ENCODED_TYPE_MAPPERS.get(valueTypeResolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + valueTypeResolver.encodedType());
        }
        return type;
    }

    @FunctionalInterface
    public interface ResolverFactory {
        Object resolve(ResultSet resultSet, String parameterIndex) throws SQLException;
    }

    @FunctionalInterface
    public interface InsertFactory {
        void insert(PreparedStatement preparedStatement, int parameterIndex, Object value) throws SQLException;
    }

    private record DefaultValueTypeResolver(Class<?> encodedType, ResolverFactory resolver, InsertFactory insertInt) implements ValueTypeResolver {
        @Override
        public Object resolve(ResultSet resultSet, String parameterIndex) throws SQLException {
            return resolver.resolve(resultSet, parameterIndex);
        }

        @Override
        public void insert(final PreparedStatement preparedStatement, final int parameterIndex, final Object value) throws SQLException {
            insertInt.insert(preparedStatement, parameterIndex, value);
        }
    }
}