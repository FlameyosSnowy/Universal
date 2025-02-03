package io.github.flameyossnowy.universal.mongodb.resolvers;

import org.bson.Document;
import org.bson.types.Binary;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, MongoValueTypeResolver> resolvers = new HashMap<>();

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
                Document::getString,
                Document::put);

        register(Integer.class,
                Integer.class,
                Document::getInteger,
                Document::put);

        register(Long.class,
                Long.class,
                Document::getLong,
                Document::put);

        register(Float.class,
                Double.class,
                (stmt, index) -> stmt.getDouble(index).floatValue(),
                Document::put);

        register(Double.class,
                Double.class,
                (stmt, index) -> stmt.getDouble(index).floatValue(),
                Document::put);

        register(int.class,
                int.class,
                Document::getInteger,
                Document::put);

        register(long.class,
                long.class,
                Document::getLong,
                Document::put);

        register(float.class,
                double.class,
                (stmt, index) -> stmt.getDouble(index).floatValue(),
                Document::put);

        register(double.class,
                double.class,
                Document::getDouble,
                Document::put);

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
                (stmt, index, value) -> stmt.put(index, value.toString()));

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
                (stmt, index, value) -> stmt.put(index, value.toString()));

        register(Serializable.class,
                byte[].class,
                (stmt, index) -> {
                    byte[] array = stmt.get(index, Binary.class).getData();
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
                        stmt.put(index, new Binary(array));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public void register(Class<?> type, Class<?> encodedType, ResolverFactory resolver, InsertFactory insert) {
        resolvers.put(type, new DefaultMongoValueTypeResolver(encodedType, resolver, insert));
    }

    public void register(Class<?> type, MongoValueTypeResolver resolver) {
        resolvers.put(type, resolver);
    }

    public MongoValueTypeResolver getResolver(Class<?> type) {
        MongoValueTypeResolver resolver = resolvers.get(type);
        if (resolver != null) return resolver;

        if (Serializable.class.isAssignableFrom(type)) {
            return resolvers.get(Serializable.class);
        }

        return null;
    }

    public String getType(@NotNull MongoValueTypeResolver resolver) {
        String type = ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
        }
        return type;
    }

    public String getType(Class<?> resolver) {
        MongoValueTypeResolver MongoValueTypeResolver = this.getResolver(resolver);
        String type = ENCODED_TYPE_MAPPERS.get(MongoValueTypeResolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + MongoValueTypeResolver.encodedType());
        }
        return type;
    }

    @FunctionalInterface
    public interface ResolverFactory {
        Object resolve(Document resultSet, String parameterIndex) throws SQLException;
    }

    @FunctionalInterface
    public interface InsertFactory {
        void insert(Document preparedStatement, String parameterIndex, Object value) throws SQLException;
    }

    private record DefaultMongoValueTypeResolver(Class<?> encodedType, ResolverFactory resolver, InsertFactory insertInt) implements MongoValueTypeResolver {
        @Override
        public Object resolve(Document resultSet, String parameterIndex) throws SQLException {
            return resolver.resolve(resultSet, parameterIndex);
        }

        @Override
        public void insert(final Document preparedStatement, final String parameterIndex, final Object value) throws SQLException {
            insertInt.insert(preparedStatement, parameterIndex, value);
        }
    }
}