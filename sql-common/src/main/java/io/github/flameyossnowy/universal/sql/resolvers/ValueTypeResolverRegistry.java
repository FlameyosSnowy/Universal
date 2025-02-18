package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.utils.FastUUID;
import io.github.flameyossnowy.universal.sql.annotations.SQLResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.util.*;
import java.math.BigInteger;
import java.math.BigDecimal;

@SuppressWarnings({"unused", "unchecked", "rawtypes"})
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, SQLValueTypeResolver<?>> resolvers = new HashMap<>();

    private static final Map<Class<?>, EncodedTypeInfo> ENCODED_TYPE_MAPPERS = Map.ofEntries(
            Map.entry(String.class, new EncodedTypeInfo("VARCHAR(255)", Types.VARCHAR)),
            Map.entry(Integer.class, new EncodedTypeInfo("INT", Types.INTEGER)),
            Map.entry(int.class, new EncodedTypeInfo("INT", Types.INTEGER)),
            Map.entry(Long.class, new EncodedTypeInfo("BIGINT", Types.BIGINT)),
            Map.entry(long.class, new EncodedTypeInfo("BIGINT", Types.BIGINT)),
            Map.entry(Double.class, new EncodedTypeInfo("DOUBLE", Types.DOUBLE)),
            Map.entry(double.class, new EncodedTypeInfo("DOUBLE", Types.DOUBLE)),
            Map.entry(Float.class, new EncodedTypeInfo("FLOAT", Types.FLOAT)),
            Map.entry(float.class, new EncodedTypeInfo("FLOAT", Types.FLOAT)),
            Map.entry(byte[].class, new EncodedTypeInfo("BLOB", Types.BLOB)),
            Map.entry(Timestamp.class, new EncodedTypeInfo("TIMESTAMP", Types.TIMESTAMP)),
            Map.entry(Time.class, new EncodedTypeInfo("TIME", Types.TIME)),
            Map.entry(Date.class, new EncodedTypeInfo("DATE", Types.DATE)),
            Map.entry(Boolean.class, new EncodedTypeInfo("BOOLEAN", Types.BOOLEAN)),
            Map.entry(boolean.class, new EncodedTypeInfo("BOOLEAN", Types.BOOLEAN)),
            Map.entry(Short.class, new EncodedTypeInfo("SMALLINT", Types.SMALLINT)),
            Map.entry(short.class, new EncodedTypeInfo("SMALLINT", Types.SMALLINT)),
            Map.entry(Byte.class, new EncodedTypeInfo("TINYINT", Types.TINYINT)),
            Map.entry(byte.class, new EncodedTypeInfo("TINYINT", Types.TINYINT)),
            Map.entry(BigDecimal.class, new EncodedTypeInfo("DECIMAL", Types.DECIMAL)),
            Map.entry(BigInteger.class, new EncodedTypeInfo("NUMERIC", Types.NUMERIC))
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
                    if (array == null || array.length == 0) return null; // Prevent reading invalid data
                    try (ByteArrayInputStream byteIn = new ByteArrayInputStream(array);
                         ObjectInputStream objectIn = new ObjectInputStream(byteIn)) {
                        return objectIn.readObject();
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException("Failed to deserialize object from database. Ensure the data is stored in a valid serialized format.", e);
                    }
                },
                (stmt, index, value) -> {
                    if (value == null) {
                        stmt.setNull(index, Types.BLOB);
                        return;
                    }
                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                         ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
                        objectOut.writeObject(value);
                        stmt.setBytes(index, byteOut.toByteArray());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to serialize object for database storage.", e);
                    }
                });
    }

    public <T> void register(Class<T> type, Class<?> encodedType, ResolverFactory<T> resolver, InsertFactory<T> inserter) {
        resolvers.put(type, new DefaultSQLValueTypeResolver<>(encodedType, resolver, inserter));
    }

    public <T> void register(Class<T> type, SQLValueTypeResolver<T> resolver) {
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

    public <T> SQLValueTypeResolver<T> getResolver(Class<T> type) {
        Objects.requireNonNull(type, "Type cannot be null");

        // Check if resolver already exists
        SQLValueTypeResolver<T> resolver = (SQLValueTypeResolver<T>) resolvers.get(type);
        if (resolver != null) return resolver;

        // Normal resolution logic for basic types
        if (Serializable.class.isAssignableFrom(type)) {
            return (SQLValueTypeResolver<T>) resolvers.get(Object.class);
        }

        SQLValueTypeResolver<T> newResolver = createResolver(type);

        if (newResolver != null) return newResolver;
        throw new IllegalArgumentException("Unknown type: " + type + " and no resolver provided.");
    }

    private <T> @Nullable SQLValueTypeResolver<T> createResolver(final @NotNull Class<?> data) {
        SQLValueTypeResolver<?> resolver;
        if (Enum.class.isAssignableFrom(data)) {
            Class<? extends Enum> enumClass = (Class<? extends Enum<?>>) data;

            registerEnum(enumClass);
            resolver = getResolver(enumClass);
        } else {
            resolver = parseResolver(data);
        }
        return (SQLValueTypeResolver<T>) resolver;
    }

    private @Nullable SQLValueTypeResolver<Object> parseResolver(final @NotNull Class<?> data) {
        SQLResolver annotation = data.getAnnotation(SQLResolver.class);
        if (annotation == null) {
            return null;
        }
        if (!SQLValueTypeResolver.class.isAssignableFrom(annotation.value())) {
            throw new IllegalArgumentException("Annotation value must be an SQLValueTypeResolver: " + annotation.value());
        }
        SQLValueTypeResolver<Object> newResolver =
                (SQLValueTypeResolver<Object>) ReflectiveMetaData.newInstance(annotation.value());
        register((Class<Object>) data, newResolver);
        return newResolver;
    }

    public String getType(@NotNull SQLValueTypeResolver<?> resolver) {
        EncodedTypeInfo type = ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
        }
        return type.sqlTypeName;
    }

    public String getType(Class<?> type) {
        SQLValueTypeResolver<?> resolver = this.getResolver(type);
        if (resolver != null) {
            EncodedTypeInfo info = ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
            return info.sqlTypeName;
        }

        return null;
    }

    public EncodedTypeInfo getEncodedType(@NotNull SQLValueTypeResolver<?> resolver) {
        EncodedTypeInfo type = ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
        if (type == null) {
            throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
        }
        return type;
    }

    public EncodedTypeInfo getEncodedType(Class<?> type) {
        SQLValueTypeResolver<?> resolver = this.getResolver(type);
        if (resolver != null) {
            return ENCODED_TYPE_MAPPERS.get(resolver.encodedType());
        }
        return null;
    }

    @FunctionalInterface
    public interface ResolverFactory<T> {
        T resolve(ResultSet resultSet, String columnLabel) throws SQLException;
    }

    @FunctionalInterface
    public interface InsertFactory<T> {
        void insert(PreparedStatement preparedStatement, int parameterIndex, T value) throws SQLException;
    }

    private record DefaultSQLValueTypeResolver<T>(Class<?> encodedType,
                                                  ResolverFactory<T> resolver,
                                                  InsertFactory<T> insertInt) implements SQLValueTypeResolver<T> {
        @Override
        public T resolve(ResultSet resultSet, String parameter) throws SQLException {
            return resolver.resolve(resultSet, parameter);
        }

        @Override
        public void insert(PreparedStatement preparedStatement, int parameter, T value) throws SQLException {
            insertInt.insert(preparedStatement, parameter, value);
        }
    }

    public record EncodedTypeInfo(String sqlTypeName, int sqlType) {
        public boolean isDigit() {
            return sqlType == Types.BIGINT || sqlType == Types.INTEGER;
        }
    }
}