package io.github.flameyossnowy.universal.mysql.resolvers;

import io.github.flameyossnowy.universal.api.FastUUID;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.util.*;
import java.math.BigInteger;
import java.math.BigDecimal;

@SuppressWarnings("unused")
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, MySQLValueTypeResolver<?>> resolvers = new HashMap<>();

    private static final Map<Class<?>, String> ENCODED_TYPE_MAPPERS = Map.ofEntries(
            Map.entry(String.class, "VARCHAR(255)"),
            Map.entry(Integer.class, "INT"),
            Map.entry(int.class, "INT"),
            Map.entry(Long.class, "BIGINT"),
            Map.entry(long.class, "BIGINT"),
            Map.entry(Double.class, "DOUBLE"),
            Map.entry(double.class, "DOUBLE"),
            Map.entry(Float.class, "FLOAT"),
            Map.entry(float.class, "FLOAT"),
            Map.entry(Timestamp.class, "TIMESTAMP"),
            Map.entry(Time.class, "TIME"),
            Map.entry(Date.class, "DATE"),
            Map.entry(Boolean.class, "BOOLEAN"),
            Map.entry(boolean.class, "BOOLEAN"),
            Map.entry(Short.class, "SMALLINT"),
            Map.entry(short.class, "SMALLINT"),
            Map.entry(Byte.class, "TINYINT"),
            Map.entry(byte.class, "TINYINT"),
            Map.entry(BigDecimal.class, "DECIMAL"),
            Map.entry(BigInteger.class, "NUMERIC")
    );

    public ValueTypeResolverRegistry() {
        register(String.class,
                String.class,
                ResultSet::getString,
                PreparedStatement::setString);

        register(Integer.class,
                Integer.class,
                ResultSet::getInt,
                PreparedStatement::setInt);

        register(Long.class,
                Long.class,
                ResultSet::getLong,
                PreparedStatement::setLong);

        register(Float.class,
                Float.class,
                ResultSet::getFloat,
                PreparedStatement::setFloat);

        register(Double.class,
                Double.class,
                ResultSet::getDouble,
                PreparedStatement::setDouble);

        register(int.class,
                int.class,
                ResultSet::getInt,
                PreparedStatement::setInt);

        register(long.class,
                long.class,
                ResultSet::getLong,
                PreparedStatement::setLong);

        register(float.class,
                float.class,
                ResultSet::getFloat,
                PreparedStatement::setFloat);

        register(double.class,
                double.class,
                ResultSet::getDouble,
                PreparedStatement::setDouble);

        register(UUID.class,
                String.class,
                (stmt, index) -> {
                    String value = stmt.getString(index);
                    if (value == null) return null;
                    return FastUUID.parseUUID(value);
                },
                (stmt, index, value) -> stmt.setString(index, value.toString())
        );

        register(Instant.class,
                long.class,
                (stmt, index) -> {
                    long value = stmt.getLong(index);
                    if (value == 0) return null;
                    try {
                        return Instant.ofEpochMilli(value);
                    } catch (IllegalArgumentException e) {
                        throw new SQLException("Invalid UUID value: " + value, e);
                    }
                },
                (stmt, index, value) -> stmt.setLong(index, value.toEpochMilli()));

        register(Time.class,
                Time.class,
                ResultSet::getTime,
                PreparedStatement::setTime);

        register(Date.class,
                Date.class,
                ResultSet::getDate,
                PreparedStatement::setDate);

        register(Boolean.class,
                boolean.class,
                ResultSet::getBoolean,
                PreparedStatement::setBoolean);

        register(boolean.class,
                boolean.class,
                ResultSet::getBoolean,
                PreparedStatement::setBoolean);

        register(Short.class,
                short.class,
                ResultSet::getShort,
                PreparedStatement::setShort);

        register(short.class,
                short.class,
                ResultSet::getShort,
                PreparedStatement::setShort);

        register(Byte.class,
                byte.class,
                ResultSet::getByte,
                PreparedStatement::setByte);

        register(byte.class,
                byte.class,
                ResultSet::getByte,
                PreparedStatement::setByte);

        register(BigDecimal.class,
                BigDecimal.class,
                ResultSet::getBigDecimal,
                PreparedStatement::setBigDecimal);

        register(BigInteger.class,
                BigInteger.class,
                (rs, index) -> {
                    BigDecimal decimal = rs.getBigDecimal(index);
                    return decimal != null ? decimal.toBigInteger() : null;
                },
                (stmt, index, value) -> stmt.setBigDecimal(index, new BigDecimal(value.toString())));

        register(byte[].class,
                byte[].class,
                ResultSet::getBytes,
                PreparedStatement::setBytes);

        register(Object.class,
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

    public <T> void register(Class<T> type, Class<?> encodedType, ResolverFactory<T> resolver, InsertFactory<T> inserter) {
        resolvers.put(type, new DefaultSQLiteValueTypeResolver<>(encodedType, resolver, inserter));
    }

    public <T> void register(Class<T> type, MySQLValueTypeResolver<T> resolver) {
        resolvers.put(type, resolver);
    }

    public <E extends Enum<E>> void registerEnum(Class<E> enumType) {
        register(enumType,
                String.class,
                (resultSet, columnLabel) -> {
                    String value = resultSet.getString(columnLabel);
                    return value != null ? Enum.valueOf(enumType, value) : null;
                },
                (preparedStatement, parameterIndex, value) -> preparedStatement.setString(parameterIndex, value.name())
        );
    }

    @SuppressWarnings("unchecked")
    public <T> MySQLValueTypeResolver<?> getResolver(Class<T> type) {
        Objects.requireNonNull(type);

        MySQLValueTypeResolver<T> resolver = (MySQLValueTypeResolver<T>) resolvers.get(type);
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
    public String getType(@NotNull MySQLValueTypeResolver<?> resolver) {
        String type = ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
        }
        return type;
    }

    public String getType(Class<?> type) {
        MySQLValueTypeResolver<?> resolver = this.getResolver(type);
        if (resolver == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        return ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
    }

    @FunctionalInterface
    public interface ResolverFactory<T> {
        T resolve(ResultSet resultSet, String columnLabel) throws SQLException;
    }

    @FunctionalInterface
    public interface InsertFactory<T> {
        void insert(PreparedStatement preparedStatement, int parameterIndex, T value) throws SQLException;
    }

    private record DefaultSQLiteValueTypeResolver<T>(Class<?> encodedType,
                                                     ResolverFactory<T> resolver,
                                                     InsertFactory<T> insertInt) implements MySQLValueTypeResolver<T> {
        @Override
        public T resolve(ResultSet resultSet, String parameter) throws SQLException {
            return resolver.resolve(resultSet, parameter);
        }

        @Override
        public void insert(PreparedStatement preparedStatement, int parameter, T value) throws SQLException {
            insertInt.insert(preparedStatement, parameter, value);
        }
    }
}