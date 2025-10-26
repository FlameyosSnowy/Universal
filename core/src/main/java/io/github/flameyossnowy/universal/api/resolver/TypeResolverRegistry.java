package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseReader;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseWriter;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.io.File;
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.*;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * A registry for managing type resolvers and their associated data handlers.
 */
public class TypeResolverRegistry {
    private final Map<Class<?>, TypeResolver<?>> resolvers = new ConcurrentHashMap<>();
    private final Map<Class<?>, DataHandler<?>> dataHandlers = new ConcurrentHashMap<>();

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

    public TypeResolverRegistry() {
        registerDefaultHandlers();
        registerDefaults();
    }

    public String getType(@NotNull TypeResolver<?> resolver) {
        String type = encodedTypeMappers.get(resolver.getType());
        if (type == null) throw new IllegalArgumentException("Unknown type: " + resolver.getType());
        return type;
    }

    public String getType(@NotNull Class<?> type) {
        return getType(getResolver(type));
    }

    public void registerDefaults() {
        registerUrlType();
        registerUriType();
        registerFileType();
        registerPathType();
        registerByteArrayType();
        registerByteBufferType();
        registerBigNumberType();
        registerCurrencyType();
        registerLocaleType();
        registerPeriodType();
        registerDurationType();
        registerInetAddressType();
        registerPatternType();
        registerModernTimeTypes();
        registerUuid();

        registerArrayType(String[].class);
        registerArrayType(Integer[].class);
        registerArrayType(Long[].class);
        registerArrayType(Double[].class);
        registerArrayType(Boolean[].class);
        registerArrayType(Byte[].class);
    }
    
    /**
     * Registers a type resolver.
     * 
     * @param resolver the resolver to register
     * @param <T> the type that the resolver handles
     */
    public <T> void register(TypeResolver<T> resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver cannot be null");
        }
        resolvers.put(resolver.getType(), resolver);
    }
    
    /**
     * Registers a data handler.
     * 
     * @param handler the data handler to register
     * @param <T> the type that the handler handles
     */
    public <T> void register(DataHandler<T> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        dataHandlers.put(handler.getType(), handler);
        // Create and register a resolver for this handler
        register(TypeResolver.fromHandler(handler));
    }
    
    /**
     * Registers a data handler using functional interfaces.
     * 
     * @param <T> the type to handle
     * @param type the type class
     * @param databaseType the database type class
     * @param sqlType the SQL type constant from {@link java.sql.Types}
     * @param reader function to read from a database
     * @param writer function to write to a database
     */
    public <T> void register(
        Class<T> type,
        Class<?> databaseType,
        int sqlType,
        DatabaseReader<T> reader,
        DatabaseWriter<T> writer
    ) {
        register(DataHandler.of(type, databaseType, sqlType, reader, writer));
    }
    
    /**
     * Gets a resolver for the specified type.
     * 
     * @param type the type to get a resolver for
     * @param <T> the type that the resolver handles
     * @return the resolver, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> TypeResolver<T> getResolver(Class<T> type) {
        return (TypeResolver<T>) resolvers.get(type);
    }
    
    /**
     * Gets a data handler for the specified type.
     * 
     * @param type the type to get a handler for
     * @param <T> the type that the handler handles
     * @return the handler, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> DataHandler<T> getHandler(Class<?> type) {
        return (DataHandler<T>) dataHandlers.get(type);
    }
    
    /**
     * Gets or creates a resolver for the specified type.
     * 
     * @param type the type to get or create a resolver for
     * @param <T> the type that the resolver handles
     * @return the resolver, or null if not found and cannot be created
     */
    @SuppressWarnings("unchecked")
    public <T> TypeResolver<T> resolve(Class<T> type) {
        // Check if we already have a resolver
        TypeResolver<T> resolver = (TypeResolver<T>) resolvers.get(type);
        if (resolver != null) {
            return resolver;
        }
        
        // Check for enum types
        if (type.isEnum()) {
            TypeResolver<T> enumResolver = (TypeResolver<T>) TypeResolver.forEnum((Class<? extends Enum>) type);
            register(enumResolver);
            return enumResolver;
        }
        
        // Check for array types
        if (type.isArray()) {
            // Handle array types if needed
            // This is a simplified example - you'd need to implement proper array handling
            return null;
        }
        
        return null;
    }
    
    /**
     * Checks if a resolver exists for the specified type.
     * 
     * @param type the type to check
     * @return true if a resolver exists, false otherwise
     */
    public boolean hasResolver(Class<?> type) {
        return resolvers.containsKey(type) || resolve(type) != null;
    }
    
    /**
     * Registers default data handlers for common Java types.
     */
    private void registerDefaultHandlers() {
        // Register primitive types
        registerPrimitive(String.class, Types.VARCHAR);
        registerPrimitive(Integer.class, Integer.TYPE, Types.INTEGER);
        registerPrimitive(Long.class, Long.TYPE, Types.BIGINT);
        registerPrimitive(Double.class, Double.TYPE, Types.DOUBLE);
        registerPrimitive(Float.class, Float.TYPE, Types.FLOAT);
        registerPrimitive(Boolean.class, Boolean.TYPE, Types.BOOLEAN);
        registerPrimitive(Short.class, Short.TYPE, Types.SMALLINT);
        registerPrimitive(Byte.class, Byte.TYPE, Types.TINYINT);
        registerPrimitive(Character.class, Character.TYPE, Types.CHAR);
        
        // Register common Java types
        register(java.util.Date.class, Types.TIMESTAMP,
            (r, c) -> r.get(c, java.util.Date.class),
            (p, i, v) -> p.set(i, v, java.util.Date.class));
            
        register(Date.class, Types.DATE,
            (r, c) -> r.get(c, Date.class),
            (p, i, v) -> p.set(i, v, Date.class));
            
        register(Time.class, Types.TIME,
            (r, c) -> r.get(c, Time.class),
            (p, i, v) -> p.set(i, v, Time.class));
            
        register(Timestamp.class, Types.TIMESTAMP,
            (r, c) -> r.get(c, Timestamp.class),
            (p, i, v) -> p.set(i, v, Timestamp.class));
            
        register(BigDecimal.class, Types.DECIMAL,
            (r, c) -> r.get(c, BigDecimal.class),
            (p, i, v) -> p.set(i, v, BigDecimal.class));
            
        register(java.util.UUID.class, Types.VARCHAR,
            (r, c) -> {
                String value = r.get(c, String.class);
                return value != null ? java.util.UUID.fromString(value) : null;
            },
            (p, i, v) -> p.set(i, v != null ? v.toString() : null, String.class));
    }
    
    /**
     * Registers a primitive type handler.
     */
    private <T> void registerPrimitive(Class<T> type, int sqlType) {
        registerPrimitive(type, type, sqlType);
    }
    
    /**
     * Registers a primitive type handler with a separate primitive type.
     */
    private <T> void registerPrimitive(Class<T> type, Class<?> primitiveType, int sqlType) {
        register(DataHandler.of(
            type,
            type,
            sqlType,
            (r, c) -> r.get(c, type),
            (p, i, v) -> p.set(i, v, primitiveType)
        ));
    }
    
    /**
     * Helper method to create and register a handler.
     */
    private <T> void register(
        Class<T> type,
        int sqlType,
        DatabaseReader<T> reader,
        DatabaseWriter<T> writer
    ) {
        register(type, type, sqlType, reader, writer);
    }

    /*
    Private registration methods
     */

    private void registerUuid() {
        this.register(new TypeResolver<UUID>() {
            @Override public Class<UUID> getType() { return UUID.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }
            @Override public UUID resolve(DatabaseResult result, String columnName) {
                String value = result.get(columnName, String.class);
                return value != null ? UUID.fromString(value) : null;
            }
            @Override public void insert(DatabaseParameters parameters, String index, UUID value) {
                parameters.set(index, value != null ? value.toString() : null, String.class);
            }
        });
    }

    private void registerModernTimeTypes() {
        // Register date/time types
        this.register(new TypeResolver<Date>() {
            @Override public Class<Date> getType() { return Date.class; }
            @Override public Class<Date> getDatabaseType() { return Date.class; }
            @Override public Date resolve(DatabaseResult result, String columnName) { return result.get(columnName, Date.class); }
            @Override public void insert(DatabaseParameters parameters, String index, Date value) { parameters.set(index, value, Date.class); }
        });

        this.register(new TypeResolver<Time>() {
            @Override public Class<Time> getType() { return Time.class; }
            @Override public Class<Time> getDatabaseType() { return Time.class; }
            @Override public Time resolve(DatabaseResult result, String columnName) { return result.get(columnName, Time.class); }
            @Override public void insert(DatabaseParameters parameters, String index, Time value) { parameters.set(index, value, Time.class); }
        });

        this.register(new TypeResolver<Timestamp>() {
            @Override public Class<Timestamp> getType() { return Timestamp.class; }
            @Override public Class<Timestamp> getDatabaseType() { return Timestamp.class; }
            @Override public Timestamp resolve(DatabaseResult result, String columnName) { return result.get(columnName, Timestamp.class); }
            @Override public void insert(DatabaseParameters parameters, String index, Timestamp value) { parameters.set(index, value, Timestamp.class); }
        });

        this.register(new TypeResolver<LocalDate>() {
            @Override public Class<LocalDate> getType() { return LocalDate.class; }
            @Override public Class<Date> getDatabaseType() { return Date.class; }
            @Override public LocalDate resolve(DatabaseResult result, String columnName) {
                Date date = result.get(columnName, Date.class);
                return date != null ? date.toLocalDate() : null;
            }
            @Override public void insert(DatabaseParameters parameters, String index, LocalDate value) {
                parameters.set(index, value != null ? Date.valueOf(value) : null, Date.class);
            }
        });

        this.register(new TypeResolver<LocalTime>() {
            @Override public Class<LocalTime> getType() { return LocalTime.class; }
            @Override public Class<Time> getDatabaseType() { return Time.class; }
            @Override public LocalTime resolve(DatabaseResult result, String columnName) {
                Time time = result.get(columnName, Time.class);
                return time != null ? time.toLocalTime() : null;
            }
            @Override public void insert(DatabaseParameters parameters, String index, LocalTime value) {
                parameters.set(index, value != null ? Time.valueOf(value) : null, Time.class);
            }
        });

        this.register(new TypeResolver<LocalDateTime>() {
            @Override public Class<LocalDateTime> getType() { return LocalDateTime.class; }
            @Override public Class<Timestamp> getDatabaseType() { return Timestamp.class; }
            @Override public LocalDateTime resolve(DatabaseResult result, String columnName) {
                Timestamp timestamp = result.get(columnName, Timestamp.class);
                return timestamp != null ? timestamp.toLocalDateTime() : null;
            }
            @Override public void insert(DatabaseParameters parameters, String index, LocalDateTime value) {
                parameters.set(index, value != null ? Timestamp.valueOf(value) : null, Timestamp.class);
            }
        });

        this.register(new TypeResolver<ZonedDateTime>() {
            @Override public Class<ZonedDateTime> getType() { return ZonedDateTime.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }
            @Override public ZonedDateTime resolve(DatabaseResult result, String columnName) {
                String value = result.get(columnName, String.class);
                return value != null ? ZonedDateTime.parse(value) : null;
            }
            @Override public void insert(DatabaseParameters parameters, String index, ZonedDateTime value) {
                parameters.set(index, value != null ? value.toString() : null, String.class);
            }
        });

        this.register(new TypeResolver<OffsetDateTime>() {
            @Override public Class<OffsetDateTime> getType() { return OffsetDateTime.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }
            @Override public OffsetDateTime resolve(DatabaseResult result, String columnName) {
                String value = result.get(columnName, String.class);
                return value != null ? OffsetDateTime.parse(value) : null;
            }
            @Override public void insert(DatabaseParameters parameters, String index, OffsetDateTime value) {
                parameters.set(index, value != null ? value.toString() : null, String.class);
            }
        });
    }

    // Caching for expensive operations
    // Caches for expensive operations
    private final Map<String, URL> urlCache = new ConcurrentHashMap<>();
    private final Map<String, URI> uriCache = new ConcurrentHashMap<>();
    private final Map<String, Path> pathCache = new ConcurrentHashMap<>();
    private final Map<String, InetAddress> inetAddressCache = new ConcurrentHashMap<>();
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /**
     * Registers a resolver for URL type with caching.
     */
    private void registerUrlType() {
        this.register(new TypeResolver<URL>() {
            @Override public Class<URL> getType() { return URL.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public URL resolve(DatabaseResult result, String columnName) {
                String urlString = result.get(columnName, String.class);
                if (urlString == null) return null;

                return urlCache.computeIfAbsent(urlString, k -> {
                    try {
                        return new URL(k);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException("Invalid URL in database: " + k, e);
                    }
                });
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, URL value) {
                String urlString = value != null ? value.toString() : null;
                parameters.set(index, urlString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for URI type with caching.
     */
    private void registerUriType() {
        this.register(new TypeResolver<URI>() {
            @Override public Class<URI> getType() { return URI.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public URI resolve(DatabaseResult result, String columnName) {
                String uriString = result.get(columnName, String.class);
                if (uriString == null) return null;

                return uriCache.computeIfAbsent(uriString, k -> {
                    try {
                        return new URI(k);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException("Invalid URI in database: " + k, e);
                    }
                });
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, URI value) {
                String uriString = value != null ? value.toString() : null;
                parameters.set(index, uriString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for File type.
     */
    private void registerFileType() {
        this.register(new TypeResolver<File>() {
            @Override public Class<File> getType() { return File.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public File resolve(DatabaseResult result, String columnName) {
                String path = result.get(columnName, String.class);
                return path != null ? new File(path) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, File value) {
                String path = value != null ? value.getPath() : null;
                parameters.set(index, path, String.class);
            }
        });
    }

    /**
     * Registers a resolver for Path type with caching.
     */
    private void registerPathType() {
        this.register(new TypeResolver<Path>() {
            @Override public Class<Path> getType() { return Path.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public Path resolve(DatabaseResult result, String columnName) {
                String pathString = result.get(columnName, String.class);
                if (pathString == null) return null;

                return pathCache.computeIfAbsent(pathString, Paths::get);
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, Path value) {
                String pathString = value != null ? value.toString() : null;
                parameters.set(index, pathString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for byte array type.
     */
    private void registerByteArrayType() {
        this.register(new TypeResolver<byte[]>() {
            @Override public Class<byte[]> getType() { return byte[].class; }
            @Override public Class<byte[]> getDatabaseType() { return byte[].class; }

            @Override
            public byte[] resolve(DatabaseResult result, String columnName) {
                return result.get(columnName, byte[].class);
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, byte[] value) {
                parameters.set(index, value, byte[].class);
            }
        });
    }

    /**
     * Registers a resolver for ByteBuffer type.
     */
    private void registerByteBufferType() {
        this.register(new TypeResolver<ByteBuffer>() {
            @Override public Class<ByteBuffer> getType() { return ByteBuffer.class; }
            @Override public Class<byte[]> getDatabaseType() { return byte[].class; }

            @Override
            public ByteBuffer resolve(DatabaseResult result, String columnName) {
                byte[] bytes = result.get(columnName, byte[].class);
                return bytes != null ? ByteBuffer.wrap(bytes) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, ByteBuffer value) {
                byte[] bytes = value != null ? value.array() : null;
                parameters.set(index, bytes, byte[].class);
            }
        });
    }

    /**
     * Registers a resolver for BigInteger type.
     */
    private void registerBigNumberType() {
        this.register(new TypeResolver<BigInteger>() {
            @Override public Class<BigInteger> getType() { return BigInteger.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public BigInteger resolve(DatabaseResult result, String columnName) {
                String value = result.get(columnName, String.class);
                return value != null ? new BigInteger(value) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, BigInteger value) {
                String strValue = value != null ? value.toString() : null;
                parameters.set(index, strValue, String.class);
            }
        });

        this.register(new TypeResolver<BigDecimal>() {
            @Override public Class<BigDecimal> getType() { return BigDecimal.class; }
            @Override public Class<BigDecimal> getDatabaseType() { return BigDecimal.class; }
            @Override public BigDecimal resolve(DatabaseResult result, String columnName) {
                return result.get(columnName, BigDecimal.class);
            }
            @Override public void insert(DatabaseParameters parameters, String index, BigDecimal value) {
                parameters.set(index, value, BigDecimal.class);
            }
        });
    }

    /**
     * Registers a resolver for Currency type.
     */
    private void registerCurrencyType() {
        this.register(new TypeResolver<Currency>() {
            @Override public Class<Currency> getType() { return Currency.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public Currency resolve(DatabaseResult result, String columnName) {
                String currencyCode = result.get(columnName, String.class);
                return currencyCode != null ? Currency.getInstance(currencyCode) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, Currency value) {
                String currencyCode = value != null ? value.getCurrencyCode() : null;
                parameters.set(index, currencyCode, String.class);
            }
        });
    }

    /**
     * Registers a resolver for Locale type.
     */
    private void registerLocaleType() {
        this.register(new TypeResolver<Locale>() {
            @Override public Class<Locale> getType() { return Locale.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public Locale resolve(DatabaseResult result, String columnName) {
                String localeString = result.get(columnName, String.class);
                return localeString != null ? Locale.forLanguageTag(localeString) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, Locale value) {
                String localeString = value != null ? value.toLanguageTag() : null;
                parameters.set(index, localeString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for Period type.
     */
    private void registerPeriodType() {
        this.register(new TypeResolver<Period>() {
            @Override public Class<Period> getType() { return Period.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public Period resolve(DatabaseResult result, String columnName) {
                String periodString = result.get(columnName, String.class);
                return periodString != null ? Period.parse(periodString) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, Period value) {
                String periodString = value != null ? value.toString() : null;
                parameters.set(index, periodString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for Duration type.
     */
    private void registerDurationType() {
        this.register(new TypeResolver<Duration>() {
            @Override public Class<Duration> getType() { return Duration.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public Duration resolve(DatabaseResult result, String columnName) {
                String durationString = result.get(columnName, String.class);
                return durationString != null ? Duration.parse(durationString) : null;
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, Duration value) {
                String durationString = value != null ? value.toString() : null;
                parameters.set(index, durationString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for InetAddress type with caching.
     */
    private void registerInetAddressType() {
        this.register(new TypeResolver<InetAddress>() {
            @Override public Class<InetAddress> getType() { return InetAddress.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public InetAddress resolve(DatabaseResult result, String columnName) {
                String hostAddress = result.get(columnName, String.class);
                if (hostAddress == null) return null;

                return inetAddressCache.computeIfAbsent(hostAddress, k -> {
                    try {
                        return InetAddress.getByName(k);
                    } catch (UnknownHostException e) {
                        throw new RuntimeException("Invalid IP address in database: " + k, e);
                    }
                });
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, InetAddress value) {
                String hostAddress = value != null ? value.getHostAddress() : null;
                parameters.set(index, hostAddress, String.class);
            }
        });
    }

    /**
     * Registers a resolver for Pattern type with caching.
     */
    private void registerPatternType() {
        this.register(new TypeResolver<Pattern>() {
            @Override public Class<Pattern> getType() { return Pattern.class; }
            @Override public Class<String> getDatabaseType() { return String.class; }

            @Override
            public Pattern resolve(DatabaseResult result, String columnName) {
                String patternString = result.get(columnName, String.class);
                if (patternString == null) return null;

                return patternCache.computeIfAbsent(patternString, Pattern::compile);
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, Pattern value) {
                String patternString = value != null ? value.pattern() : null;
                parameters.set(index, patternString, String.class);
            }
        });
    }

    /**
     * Registers a resolver for array types.
     *
     * @param <T> the component type of the array
     * @param arrayType the array type class
     */
    @SuppressWarnings("unchecked")
    public <T> void registerArrayType(Class<T[]> arrayType) {
        Class<T> componentType = (Class<T>) arrayType.getComponentType();

        this.register(new TypeResolver<T[]>() {
            @Override public Class<T[]> getType() { return arrayType; }
            @Override public Class<Object> getDatabaseType() { return Object.class; }

            @Override
            public T[] resolve(DatabaseResult result, String columnName) {
                Object array = result.get(columnName, Object.class);
                if (array == null) {
                    return null;
                }

                if (array instanceof Object[]) {
                    return (T[]) array;
                }

                if (array instanceof java.sql.Array) {
                    try {
                        return (T[]) ((java.sql.Array) array).getArray();
                    } catch (SQLException e) {
                        throw new RuntimeException("Error getting array from result set", e);
                    }
                }

                return (T[]) Array.newInstance(componentType, 1);
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, T[] value) {
                parameters.set(index, value, Object.class);
            }
        });
    }
}
