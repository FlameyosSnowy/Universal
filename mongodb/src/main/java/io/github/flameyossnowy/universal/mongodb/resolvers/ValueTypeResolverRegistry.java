package io.github.flameyossnowy.universal.mongodb.resolvers;

import org.bson.Document;
import org.bson.types.Binary;

import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@SuppressWarnings("unused")
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, MongoValueTypeResolver<?>> resolvers = new HashMap<>();

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
                (stmt, index, value) -> stmt.put(index, value.doubleValue()));

        register(Double.class,
                Double.class,
                Document::getDouble,
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

        register(Object.class,
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

    public <T> void register(Class<T> type, Class<?> encodedType, ResolverFactory<T> resolver, InsertFactory<T> insert) {
        resolvers.put(type, new DefaultMongoValueTypeResolver<>(encodedType, resolver, insert));
    }

    public <T> void register(Class<?> type, MongoValueTypeResolver<T> resolver) {
        resolvers.put(type, resolver);
    }

    @SuppressWarnings("unchecked")
    public <T> MongoValueTypeResolver<?> getResolver(Class<T> type) {
        Objects.requireNonNull(type);
        if (type == Object.class) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }

        MongoValueTypeResolver<T> resolver = (MongoValueTypeResolver<T>) resolvers.get(type);
        if (resolver != null) {
            return resolver;
        }

        if (Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Serializable.class.isAssignableFrom(type)) {
            return resolvers.get(Object.class);
        }

        return null;
    }

    @FunctionalInterface
    public interface ResolverFactory<T> {
        T resolve(Document resultSet, String parameterIndex) throws SQLException;
    }

    @FunctionalInterface
    public interface InsertFactory<T> {
        void insert(Document preparedStatement, String parameterIndex, T value) throws SQLException;
    }

    private record DefaultMongoValueTypeResolver<T>(Class<?> encodedType, ResolverFactory<T> resolver, InsertFactory<T> insertInt) implements MongoValueTypeResolver<T> {
        @Override
        public T resolve(Document resultSet, String parameterIndex) throws SQLException {
            return resolver.resolve(resultSet, parameterIndex);
        }

        @Override
        public void insert(final Document preparedStatement, final String parameterIndex, final T value) throws SQLException {
            insertInt.insert(preparedStatement, parameterIndex, value);
        }
    }
}