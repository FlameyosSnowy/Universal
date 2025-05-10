package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.util.*;
import java.net.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
@SuppressWarnings({"unused", "unchecked", "UnnecessaryBoxing"})
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, SQLValueTypeResolver<?>> resolvers = new ConcurrentHashMap<>(36);

    private final Map<String, URL> urlPool = new ConcurrentHashMap<>(5);
    private final Map<String, URI> uriPool = new ConcurrentHashMap<>(5);
    private final Map<String, InetAddress> addressPool = new ConcurrentHashMap<>(5);
    private final Map<Integer, NetworkInterface> networkPool = new ConcurrentHashMap<>(5);

    public static boolean URL_CACHING_ENABLED = false, URI_CACHING_ENABLED = false, INET_ADDRESS_CACHING_ENABLED = false, NETWORK_INTERFACE_CACHING_ENABLED = false;

    private final Map<Class<?>, String> encodedTypeMappers = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry(String.class, "VARCHAR(255)"),

            Map.entry(Integer.class, "INT"),
            Map.entry(int.class, "INT"),
            Map.entry(Long.class, "BIGINT"),
            Map.entry(long.class, "BIGINT"),
            Map.entry(Double.class, "DOUBLE"),
            Map.entry(double.class, "DOUBLE"),
            Map.entry(Float.class, "FLOAT"),
            Map.entry(byte[].class, "BLOB"),

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
            Map.entry(BigInteger.class, "NUMERIC"),

            Map.entry(LocalDate.class, "DATE"),
            Map.entry(LocalTime.class, "TIME"),
            Map.entry(LocalDateTime.class, "TIMESTAMP"),
            Map.entry(OffsetDateTime.class, "VARCHAR(64)"),
            Map.entry(ZonedDateTime.class, "VARCHAR(64)"),
            Map.entry(Duration.class, "BIGINT"),
            Map.entry(Period.class, "VARCHAR(32)"),

            Map.entry(URI.class, "VARCHAR(255)"),
            Map.entry(URL.class, "VARCHAR(255)"),
            Map.entry(InetAddress.class, "VARCHAR(255)"),
            Map.entry(NetworkInterface.class, "INT"),

            Map.entry(Class.class, "VARCHAR(255)"),
            Map.entry(Locale.class, "VARCHAR(255)"),
            Map.entry(Currency.class, "VARCHAR(255)")
    ));

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXED_MAPPER = Map.ofEntries(
        Map.entry(int.class, Integer.class),
        Map.entry(long.class, Long.class),
        Map.entry(float.class, Float.class),
        Map.entry(double.class, Double.class),
        Map.entry(byte.class, Byte.class),
        Map.entry(short.class, Short.class),
        Map.entry(boolean.class, Boolean.class)
    );

    public static final ValueTypeResolverRegistry INSTANCE = new ValueTypeResolverRegistry();

    private ValueTypeResolverRegistry() {
        register(String.class,
                String.class,
                ResultSet::getString,
                PreparedStatement::setString);

        register(Integer.class,
                Integer.class,
                (stmt, index) -> Integer.valueOf(stmt.getInt(index)),
                PreparedStatement::setInt);

        register(Long.class,
                Long.class,
                (stmt, index) -> Long.valueOf(stmt.getLong(index)),
                PreparedStatement::setLong);

        register(Float.class,
                Float.class,
                (stmt, index) -> Float.valueOf(stmt.getFloat(index)),
                PreparedStatement::setFloat);

        register(Double.class,
                Double.class,
                (stmt, index) -> Double.valueOf(stmt.getDouble(index)),
                PreparedStatement::setDouble);

        register(Short.class,
                Short.class,
                (stmt, index) -> Short.valueOf(stmt.getShort(index)),
                PreparedStatement::setShort);

        register(UUID.class,
                String.class,
                (stmt, index) -> {
                    String value = stmt.getString(index);
                    if (value == null) return null;
                    return UUID.fromString(value);
                },
                (stmt, index, value) -> stmt.setString(index, value == null ? null : value.toString())
        );

        register(Instant.class,
                long.class,
                (stmt, index) -> {
                    long value = stmt.getLong(index);
                    if (value == 0) return null;
                    try {
                        return Instant.ofEpochMilli(value);
                    } catch (DateTimeException e) {
                        throw new SQLException("Invalid Instant value: " + value, e);
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
                Boolean.class,
                ResultSet::getBoolean,
                PreparedStatement::setBoolean);

        register(Byte.class,
                byte.class,
                (stmt, index) -> {
                    byte value = stmt.getByte(index);
                    return Byte.valueOf(value);
                },
                PreparedStatement::setByte);


        register(BigDecimal.class,
                BigDecimal.class,
                ResultSet::getBigDecimal,
                PreparedStatement::setBigDecimal);

        register(BigInteger.class,
                BigDecimal.class,
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

        register(LocalDate.class,
                Date.class,
                (rs, idx) -> {
                    Date date = rs.getDate(idx);
                    return date != null ? date.toLocalDate() : null;
                },
                (stmt, idx, value) -> stmt.setDate(idx, Date.valueOf(value)));

        register(LocalTime.class,
                Time.class,
                (rs, idx) -> {
                    Time time = rs.getTime(idx);
                    return time != null ? time.toLocalTime() : null;
                },
                (stmt, idx, value) -> stmt.setTime(idx, Time.valueOf(value)));

        register(LocalDateTime.class,
                Timestamp.class,
                (rs, idx) -> {
                    Timestamp ts = rs.getTimestamp(idx);
                    return ts != null ? ts.toLocalDateTime() : null;
                },
                (stmt, idx, value) -> stmt.setTimestamp(idx, Timestamp.valueOf(value)));

        register(OffsetDateTime.class,
                String.class,
                (rs, idx) -> {
                    String iso = rs.getString(idx);
                    return iso != null ? OffsetDateTime.parse(iso) : null;
                },
                (stmt, idx, value) -> stmt.setString(idx, value != null ? value.toString() : null));

        register(ZonedDateTime.class,
                String.class,
                (rs, idx) -> {
                    String iso = rs.getString(idx);
                    return iso != null ? ZonedDateTime.parse(iso) : null;
                },
                (stmt, idx, value) -> stmt.setString(idx, value != null ? value.toString() : null));

        register(Duration.class,
                long.class,
                (rs, idx) -> {
                    long millis = rs.getLong(idx);
                    return rs.wasNull() || millis == 0 ? null : Duration.ofMillis(millis);
                },
                (stmt, idx, value) -> stmt.setLong(idx, value.toMillis()));

        register(Period.class,
                String.class,
                (rs, idx) -> {
                    String iso = rs.getString(idx);
                    return iso != null ? Period.parse(iso) : null;
                },
                (stmt, idx, value) -> stmt.setString(idx, value.toString()));


        register(Year.class,
                long.class,
                (rs, idx) -> {
                    long epochMillis = rs.getLong(idx);
                    return epochMillis != 0 ? Year.from(Instant.ofEpochMilli(epochMillis)) : null;
                },
                (stmt, idx, value) -> stmt.setLong(idx, Instant.from(value).toEpochMilli()));

        register(InetAddress.class,
                String.class,
                (rs, i) -> {
                    String host = rs.getString(i);
                    if (host == null) return null;

                    return INET_ADDRESS_CACHING_ENABLED ? addressPool.computeIfAbsent(host, ValueTypeResolverRegistry::createInetAddress) : createInetAddress(host);
                },
                (stmt, i, value) -> stmt.setString(i, value != null ? value.getHostAddress() : null)
        );

        register(URI.class,
                String.class,
                (rs, i) -> {
                    String uri = rs.getString(i);
                    if (uri == null) return null;

                    return URI_CACHING_ENABLED ? uriPool.computeIfAbsent(uri, URI::create) : URI.create(uri);
                },
                (stmt, i, value) -> stmt.setString(i, value != null ? value.toString() : null)
        );

        register(URL.class,
                String.class,
                (rs, i) -> {
                    String url = rs.getString(i);
                    if (url == null) return null;
                    return URL_CACHING_ENABLED ? urlPool.computeIfAbsent(url, ValueTypeResolverRegistry::createNewURL) : createNewURL(url);
                },
                (stmt, i, value) -> stmt.setString(i, value != null ? value.toString() : null)
        );

        register(NetworkInterface.class,
                int.class,
                (rs, i) -> {
                    int index = rs.getInt(i);
                    if (rs.wasNull() || index == 0) return null;
                    return NETWORK_INTERFACE_CACHING_ENABLED
                                ? networkPool.computeIfAbsent(index, ValueTypeResolverRegistry::createNewNetworkInterface)
                                : createNewNetworkInterface(index);
                },
                (stmt, i, value) -> stmt.setInt(i, value.getIndex())
        );

        register(Class.class,
                String.class,
                (rs, i) -> {
                    String className = rs.getString(i);
                    try {
                        return className != null ? Class.forName(className) : null;
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                },
                (stmt, i, value) -> stmt.setString(i, value != null ? value.getName() : null)
        );

        register(Locale.class,
                String.class,
                (rs, i) -> {
                    String localeStr = rs.getString(i);
                    return localeStr != null ? Locale.forLanguageTag(localeStr) : null;
                },
                (stmt, i, value) -> stmt.setString(i, value != null ? value.toLanguageTag() : null)
        );

        register(Currency.class,
                String.class,
                (rs, i) -> {
                    String code = rs.getString(i);
                    return code != null ? Currency.getInstance(code) : null;
                },
                (stmt, i, value) -> stmt.setString(i, value != null ? value.getCurrencyCode() : null)
        );
    }

    private static InetAddress createInetAddress(String h) {
        try {
            return InetAddress.getByName(h);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull URL createNewURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull NetworkInterface createNewNetworkInterface(int index) {
        try {
            return NetworkInterface.getByIndex(index);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void registerEncodedTypeMapper(Class<T> type, String name) {
        this.encodedTypeMappers.put(type, name);
    }

    public <T> SQLValueTypeResolver<T> register(Class<T> type, Class<?> encodedType, ResolverFactory<T> resolver, InsertFactory<T> inserter) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(encodedType, "Encoded type cannot be null");
        Objects.requireNonNull(resolver, "Resolver cannot be null");
        Objects.requireNonNull(inserter, "Inserter cannot be null");

        SQLValueTypeResolver<T> resolverType = new DefaultSQLValueTypeResolver<>(encodedType, resolver, inserter);
        resolvers.put(type, resolverType);
        return resolverType;
    }

    public <T> SQLValueTypeResolver<T> register(Class<T> type, SQLValueTypeResolver<T> resolver) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(resolver, "Resolver cannot be null");

        resolvers.put(type, resolver);
        return resolver;
    }

    public <E extends Enum<E>> SQLValueTypeResolver<E> registerEnum(Class<E> enumType) {
        return register(enumType,
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

        Class<?> primitiveType = PRIMITIVE_TO_BOXED_MAPPER.get(type);
        if (primitiveType != null) {
            return (SQLValueTypeResolver<T>) resolvers.get(primitiveType);
        }

        // Normal resolution logic for basic types
        if (Serializable.class.isAssignableFrom(type)) {
            return (SQLValueTypeResolver<T>) resolvers.get(Object.class);
        }

        return null;
    }

    public String getType(@NotNull SQLValueTypeResolver<?> resolver) {
        String type = encodedTypeMappers.get(resolver.encodedType());
        if (type == null) throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
        return type;
    }

    public String getType(Class<?> type) {
        RepositoryInformation information;
        if ((information = RepositoryMetadata.getMetadata(type)) != null) return getType(information.getPrimaryKey().type());

        SQLValueTypeResolver<?> resolver = this.getResolver(type);
        if (resolver != null) {
            String info = encodedTypeMappers.get(resolver.encodedType());
            if (info == null) throw new IllegalArgumentException("Unknown type: " + resolver.encodedType());
            return info;
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
}