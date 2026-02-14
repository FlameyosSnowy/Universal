package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseReader;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseWriter;
import io.github.flameyossnowy.universal.api.json.DefaultJsonCodec;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.velocis.cache.algorithms.LRUCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class TypeResolverRegistry {
    private final Map<ResolverKey, TypeResolver<?>> resolvers = new ConcurrentHashMap<>(24);
    private final Map<Class<?>, DataHandler<?>> dataHandlers = new ConcurrentHashMap<>(24);
    private final LRUCache<ResolverKey, TypeResolver<?>> assignableCache = new LRUCache<>(32);

    private final Map<Class<? extends JsonCodec<?>>, JsonCodec<?>> jsonCodecs = new ConcurrentHashMap<>(8);
    private volatile Supplier<ObjectMapper> objectMapperSupplier;

    private static final TypeResolver<?> NULL_MARKER = new TypeResolver<>() {
        @Override
        public @Nullable Class<Object> getType() {
            return null;
        }

        @Override
        public @Nullable Class<?> getDatabaseType() {
            return null;
        }

        @Override
        public @Nullable Object resolve(DatabaseResult result, String columnName) {
            return null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Object value) {

        }
    };

    private final Map<Class<?>, SqlTypeMapping> sqlTypeMappings =
        new ConcurrentHashMap<>(
            Map.ofEntries(
                Map.entry(String.class, SqlTypeMapping.of("TEXT")),

                Map.entry(Integer.class, SqlTypeMapping.of("INT")),
                Map.entry(int.class, SqlTypeMapping.of("INT")),

                Map.entry(Long.class, SqlTypeMapping.of("BIGINT")),
                Map.entry(long.class, SqlTypeMapping.of("BIGINT")),

                Map.entry(Double.class, SqlTypeMapping.of("DOUBLE")),
                Map.entry(double.class, SqlTypeMapping.of("DOUBLE")),

                Map.entry(Float.class, SqlTypeMapping.of("FLOAT")),
                Map.entry(byte[].class, SqlTypeMapping.of("BLOB")),

                Map.entry(UUID.class,
                    SqlTypeMapping.of("VARCHAR(36)", "BINARY(16)")),

                Map.entry(InetAddress.class,
                    SqlTypeMapping.of("TEXT", "BINARY(16)")),

                Map.entry(Boolean.class, SqlTypeMapping.of("BOOLEAN")),
                Map.entry(boolean.class, SqlTypeMapping.of("BOOLEAN")),

                Map.entry(Short.class, SqlTypeMapping.of("SMALLINT")),
                Map.entry(short.class, SqlTypeMapping.of("SMALLINT")),

                Map.entry(Byte.class, SqlTypeMapping.of("TINYINT")),
                Map.entry(byte.class, SqlTypeMapping.of("TINYINT")),

                Map.entry(BigDecimal.class, SqlTypeMapping.of("DECIMAL")),
                Map.entry(BigInteger.class, SqlTypeMapping.of("NUMERIC")),

                Map.entry(Instant.class, SqlTypeMapping.of("TEXT", "BIGINT")),
                Map.entry(OffsetTime.class, SqlTypeMapping.of("TEXT", "TEXT")),
                Map.entry(Year.class, SqlTypeMapping.of("INT", "INT")),
                Map.entry(Month.class, SqlTypeMapping.of("INT", "INT")),
                Map.entry(YearMonth.class, SqlTypeMapping.of("TEXT", "TEXT")),
                Map.entry(MonthDay.class, SqlTypeMapping.of("TEXT", "TEXT")),
                Map.entry(TimeZone.class, SqlTypeMapping.of("TEXT", "TEXT")),
                Map.entry(ZoneId.class, SqlTypeMapping.of("TEXT", "TEXT")),
                Map.entry(LocalDate.class, SqlTypeMapping.of("DATE")),
                Map.entry(LocalTime.class, SqlTypeMapping.of("TIME")),
                Map.entry(LocalDateTime.class, SqlTypeMapping.of("TIMESTAMP")),

                Map.entry(OffsetDateTime.class,
                    SqlTypeMapping.of("TEXT")),

                Map.entry(ZonedDateTime.class,
                    SqlTypeMapping.of("TEXT")),

                Map.entry(Duration.class, SqlTypeMapping.of("BIGINT")),
                Map.entry(Period.class, SqlTypeMapping.of("TEXT")),

                Map.entry(URI.class, SqlTypeMapping.of("TEXT")),
                Map.entry(URL.class, SqlTypeMapping.of("TEXT")),
                Map.entry(Pattern.class, SqlTypeMapping.of("TEXT")),

                Map.entry(Class.class, SqlTypeMapping.of("TEXT")),
                Map.entry(Locale.class, SqlTypeMapping.of("TEXT")),
                Map.entry(Currency.class, SqlTypeMapping.of("TEXT"))
            )
        );

    public TypeResolverRegistry() {
        registerDefaultHandlers();
        registerDefaults();
    }

    /**
     * Configure the ObjectMapper supplier used to instantiate codecs that require it
     * (e.g. {@link DefaultJsonCodec}).
     */
    public void setObjectMapperSupplier(@Nullable Supplier<ObjectMapper> objectMapperSupplier) {
        this.objectMapperSupplier = objectMapperSupplier;
    }

    @SuppressWarnings("unchecked")
    public <T> @NotNull JsonCodec<T> getJsonCodec(@NotNull Class<? extends JsonCodec<?>> codecClass) {
        if (codecClass == null) {
            throw new IllegalArgumentException("codecClass cannot be null");
        }

        return (JsonCodec<T>) jsonCodecs.computeIfAbsent(codecClass, c -> {
            try {
                // Special-case DefaultJsonCodec(ObjectMapper)
                if (DefaultJsonCodec.class.equals(c)) {
                    Supplier<ObjectMapper> supplier = this.objectMapperSupplier;
                    if (supplier == null) {
                        throw new IllegalStateException(
                            "DefaultJsonCodec requires an ObjectMapper, but no ObjectMapper supplier was configured"
                        );
                    }
                    return new DefaultJsonCodec<>(supplier.get());
                }

                // Prefer no-args ctor
                try {
                    Constructor<?> ctor = c.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    return (JsonCodec<?>) ctor.newInstance();
                } catch (NoSuchMethodException ignored) {
                    // fall through
                }

                // Fallback: ObjectMapper ctor if available
                Supplier<ObjectMapper> supplier = this.objectMapperSupplier;
                if (supplier != null) {
                    try {
                        Constructor<?> ctor = c.getDeclaredConstructor(ObjectMapper.class);
                        ctor.setAccessible(true);
                        return (JsonCodec<?>) ctor.newInstance(supplier.get());
                    } catch (NoSuchMethodException ignored) {
                        // fall through
                    }
                }

                throw new IllegalStateException(
                    "Cannot instantiate JsonCodec: " + c.getName() + ". Provide a public no-args constructor " +
                        "or an ObjectMapper constructor (and configure an ObjectMapper supplier)."
                );
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate JsonCodec: " + c.getName(), e);
            }
        });
    }

    @Nullable
    public String getType(TypeResolver<?> resolver) {
        if (resolver == null) return null;
        return getType(resolver.getDatabaseType());
    }

    public @Nullable String getType(@NotNull Class<?> type) {
        SqlTypeMapping mapping = sqlTypeMappings.get(type);
        if (mapping == null) {
            mapping = sqlTypeMappings.get(this.resolve(type).getType());
        }
        return mapping != null
            ? mapping.resolve(SqlEncoding.VISUAL)
            : null;
    }

    public @Nullable String getType(Class<?> type, SqlEncoding encoding) {
        SqlTypeMapping mapping = sqlTypeMappings.get(type);
        if (mapping != null) {
            return mapping.resolve(encoding);
        }

        TypeResolver<?> resolver = this.resolve(type);
        if (resolver == null) return null;

        Class<?> dbType = resolver.getDatabaseType();
        mapping = sqlTypeMappings.get(dbType);

        return mapping != null ? mapping.resolve(encoding) : null;
    }

    public @Nullable String getType(TypeResolver<?> type, SqlEncoding encoding) {
        SqlTypeMapping mapping = sqlTypeMappings.get(type.getType());
        return mapping != null ? mapping.resolve(encoding) : null;
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
        registerArrayType(Short[].class);
        registerArrayType(Byte[].class);
    }

    private <T> void registerInternal(TypeResolver<T> resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver cannot be null");
        }
        resolvers.put(
            new ResolverKey(resolver.getType(), resolver.getEncoding()),
            resolver
        );
    }

    public <T> void register(TypeResolver<T> resolver) {
        registerInternal(resolver);
        sqlTypeMappings.put(resolver.getType(), sqlTypeMappings.get(resolver.getDatabaseType()));
        assignableCache.remove(new ResolverKey(resolver.getType(), resolver.getEncoding()));
    }

    public <T> void register(DataHandler<T> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        dataHandlers.put(handler.getType(), handler);
        registerInternal(TypeResolver.fromHandler(handler));
    }

    public <T> void register(
        Class<T> type,
        Class<?> databaseType,
        int sqlType,
        DatabaseReader<T> reader,
        DatabaseWriter<T> writer
    ) {
        register(DataHandler.of(type, databaseType, sqlType, reader, writer));
    }

    @SuppressWarnings("unchecked")
    public <T> DataHandler<T> getHandler(Class<?> type) {
        return (DataHandler<T>) dataHandlers.get(type);
    }

    public <T> TypeResolver<T> resolve(Class<T> type) {
        TypeResolver<T> visual = resolve(type, SqlEncoding.VISUAL);
        return visual != null ? visual : resolve(type, SqlEncoding.BINARY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> @Nullable TypeResolver<T> resolve(Class<T> type, SqlEncoding encoding) {
        ResolverKey key = new ResolverKey(type, encoding);

        // 1. Direct lookup
        Object direct = resolvers.get(key);
        if (direct == NULL_MARKER) return null;
        if (direct != null) return (TypeResolver<T>) direct;

        // 2. Assignable cache lookup (FIXED)
        Object cached = assignableCache.get(key);
        if (cached == NULL_MARKER) return null;
        if (cached != null) return (TypeResolver<T>) cached;

        if (type.isEnum()) {
            TypeResolver<T> enumResolver =
                (TypeResolver<T>) TypeResolver.forEnum((Class<? extends Enum>) type);

            resolvers.put(key, enumResolver);
            return enumResolver;
        }

        for (var entry : resolvers.entrySet()) {
            ResolverKey registered = entry.getKey();

            if (registered.encoding != encoding) continue;

            if (registered.type == Enum.class) continue;

            if (registered.type.isAssignableFrom(type)) {
                TypeResolver<?> resolver = entry.getValue();
                assignableCache.put(key, resolver);
                return (TypeResolver<T>) resolver;
            }
        }

        // 5. Negative caching
        assignableCache.put(key, NULL_MARKER);
        return null;
    }


    public boolean hasResolver(Class<?> type) {
        return resolve(type) != null;
    }

    private void registerDefaultHandlers() {
        registerString();
        registerPrimitive(Integer.class);
        registerPrimitive(Long.class);
        registerPrimitive(Double.class);
        registerPrimitive(Float.class);
        registerPrimitive(Boolean.class);
        registerPrimitive(Short.class);
        registerPrimitive(Byte.class);
        registerPrimitive(int.class);
        registerPrimitive(long.class);
        registerPrimitive(double.class);
        registerPrimitive(float.class);
        registerPrimitive(boolean.class);
        registerPrimitive(short.class);
        registerPrimitive(byte.class);
        registerPrimitive(char.class);

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

        register(UUID.class, Types.VARCHAR,
            (r, c) -> {
                String value = r.get(c, String.class);
                return value != null ? UUID.fromString(value) : null;
            },
            (p, i, v) -> p.set(i, v != null ? v.toString() : null, String.class));
    }

    private void registerString() {
        registerPrimitive(String.class);
    }

    private <T> void registerPrimitive(Class<T> type) {
        this.registerInternal(new TypeResolver<T>() {
            @Override
            public Class<T> getType() {
                return type;
            }

            @Override
            public Class<?> getDatabaseType() {
                return type;
            }

            @Override
            public T resolve(DatabaseResult result, String columnName) {
                return result.get(columnName, type);
            }

            @Override
            public void insert(DatabaseParameters parameters, String index, T value) {
                parameters.setRaw(index, value, type);
            }
        });
    }

    private <T> void register(
        Class<T> type,
        int sqlType,
        DatabaseReader<T> reader,
        DatabaseWriter<T> writer
    ) {
        register(type, type, sqlType, reader, writer);
    }

    private void registerUuid() {
        registerInternal(new UuidTypeResolver());
        registerInternal(new BinaryUuidTypeResolver());
    }

    private void registerModernTimeTypes() {
        registerInternal(new DateTypeResolver());
        registerInternal(new TimeTypeResolver());
        registerInternal(new TimestampTypeResolver());
        registerInternal(new LocalDateTypeResolver());
        registerInternal(new LocalTimeTypeResolver());
        registerInternal(new LocalDateTimeTypeResolver());
        registerInternal(new ZonedDateTimeTypeResolver());
        registerInternal(new OffsetDateTimeTypeResolver());
        registerInternal(new InstantTypeResolver());
        registerInternal(new EpochInstantTypeResolver());

        registerInternal(new YearTypeResolver());
        registerInternal(new MonthTypeResolver());
        registerInternal(new YearMonthTypeResolver());
        registerInternal(new MonthDayTypeResolver());
        registerInternal(new ZoneIdTypeResolver());
        registerInternal(new TimeZoneTypeResolver());
        registerInternal(new OffsetTimeTypeResolver());
    }

    private void registerUrlType() {
        registerInternal(new UrlTypeResolver());
    }

    private void registerUriType() {
        registerInternal(new UriTypeResolver());
    }

    private void registerFileType() {
        registerInternal(new FileTypeResolver());
    }

    private void registerPathType() {
        registerInternal(new PathTypeResolver());
    }

    private void registerByteArrayType() {
        registerInternal(new ByteArrayTypeResolver());
    }

    private void registerByteBufferType() {
        registerInternal(new ByteBufferTypeResolver());
    }

    private void registerBigNumberType() {
        registerInternal(new BigIntegerTypeResolver());
        registerInternal(new BigDecimalTypeResolver());
    }

    private void registerCurrencyType() {
        registerInternal(new CurrencyTypeResolver());
    }

    private void registerLocaleType() {
        registerInternal(new LocaleTypeResolver());
    }

    private void registerPeriodType() {
        registerInternal(new PeriodTypeResolver());
    }

    private void registerDurationType() {
        registerInternal(new DurationTypeResolver());
    }

    private void registerInetAddressType() {
        registerInternal(new InetAddressTypeResolver());
        registerInternal(new BinaryInetAddressTypeResolver());
    }

    private void registerPatternType() {
        registerInternal(new PatternTypeResolver());
    }

    @SuppressWarnings("unchecked")
    public <T> void registerArrayType(Class<T[]> arrayType) {
        Class<T> componentType = (Class<T>) arrayType.getComponentType();
        registerInternal(new ArrayTypeResolver<>(arrayType, componentType));
    }

    // ========================================================================
    // Static Inner Classes for TypeResolvers
    // ========================================================================

    public static final class UrlTypeResolver implements TypeResolver<URL> {
        private static final Map<String, URL> CACHE = new ConcurrentHashMap<>(3);

        @Override public Class<URL> getType() { return URL.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable URL resolve(DatabaseResult result, String columnName) {
            String urlString = result.get(columnName, String.class);
            if (urlString == null) return null;
            return CACHE.computeIfAbsent(urlString, s -> {
                try { return new URI(s).toURL(); }
                catch (MalformedURLException e) { throw new RuntimeException("Invalid URL in database: " + s, e); }
                catch (URISyntaxException e) { throw new RuntimeException(e); }
            });
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, URL value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class UriTypeResolver implements TypeResolver<URI> {
        private static final Map<String, URI> CACHE = new ConcurrentHashMap<>(3);

        @Override public Class<URI> getType() { return URI.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable URI resolve(DatabaseResult result, String columnName) {
            String uriString = result.get(columnName, String.class);
            if (uriString == null) return null;
            return CACHE.computeIfAbsent(uriString, s -> {
                try { return new URI(s); }
                catch (URISyntaxException e) { throw new RuntimeException("Invalid URI in database: " + s, e); }
            });
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, URI value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class FileTypeResolver implements TypeResolver<File> {
        @Override public Class<File> getType() { return File.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable File resolve(DatabaseResult result, String columnName) {
            String path = result.get(columnName, String.class);
            return path != null ? new File(path) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, File value) {
            parameters.set(index, value != null ? value.getPath() : null, String.class);
        }
    }

    public static final class PathTypeResolver implements TypeResolver<Path> {
        private static final Map<String, Path> CACHE = new ConcurrentHashMap<>(3);

        @Override public Class<Path> getType() { return Path.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Path resolve(DatabaseResult result, String columnName) {
            String pathString = result.get(columnName, String.class);
            if (pathString == null) return null;
            return CACHE.computeIfAbsent(pathString, Paths::get);
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Path value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class ByteArrayTypeResolver implements TypeResolver<byte[]> {
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
    }

    public static final class ByteBufferTypeResolver implements TypeResolver<ByteBuffer> {
        @Override public Class<ByteBuffer> getType() { return ByteBuffer.class; }
        @Override public Class<byte[]> getDatabaseType() { return byte[].class; }

        @Override
        public @Nullable ByteBuffer resolve(DatabaseResult result, String columnName) {
            byte[] bytes = result.get(columnName, byte[].class);
            return bytes != null ? ByteBuffer.wrap(bytes) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, ByteBuffer value) {
            byte[] bytes = value != null ? value.array() : null;
            parameters.set(index, bytes, byte[].class);
        }
    }

    public static final class BigIntegerTypeResolver implements TypeResolver<BigInteger> {
        @Override public Class<BigInteger> getType() { return BigInteger.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable BigInteger resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            if (raw == null) return null;

            if (raw instanceof BigInteger bi) {
                return bi;
            }
            if (raw instanceof String s) {
                return new BigInteger(s);
            }
            if (raw instanceof Number n) {
                return BigInteger.valueOf(n.longValue());
            }

            throw new IllegalArgumentException(
                "Unsupported BigInteger type for column '" + columnName + "': " + raw.getClass().getName()
            );
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, BigInteger value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class BigDecimalTypeResolver implements TypeResolver<BigDecimal> {
        @Override public Class<BigDecimal> getType() { return BigDecimal.class; }
        @Override public Class<BigDecimal> getDatabaseType() { return BigDecimal.class; }

        @Override
        public @Nullable BigDecimal resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            return switch (raw) {
                case null -> null;
                case BigDecimal bd -> bd;
                case String s -> new BigDecimal(s);
                case Number n -> BigDecimal.valueOf(n.doubleValue());
                default -> throw new IllegalArgumentException(
                    "Unsupported BigDecimal type for column '" + columnName + "': " + raw.getClass().getName()
                );
            };

        }

        @Override
        public void insert(DatabaseParameters parameters, String index, BigDecimal value) {
            parameters.set(index, value, BigDecimal.class);
        }
    }

    public static final class CurrencyTypeResolver implements TypeResolver<Currency> {
        @Override public Class<Currency> getType() { return Currency.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Currency resolve(DatabaseResult result, String columnName) {
            String currencyCode = result.get(columnName, String.class);
            return currencyCode != null ? Currency.getInstance(currencyCode) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Currency value) {
            parameters.set(index, value != null ? value.getCurrencyCode() : null, String.class);
        }
    }

    public static final class LocaleTypeResolver implements TypeResolver<Locale> {
        @Override public Class<Locale> getType() { return Locale.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Locale resolve(DatabaseResult result, String columnName) {
            String localeString = result.get(columnName, String.class);
            return localeString != null ? Locale.forLanguageTag(localeString) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Locale value) {
            parameters.set(index, value != null ? value.toLanguageTag() : null, String.class);
        }
    }

    public static final class PeriodTypeResolver implements TypeResolver<Period> {
        @Override public Class<Period> getType() { return Period.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Period resolve(DatabaseResult result, String columnName) {
            String periodString = result.get(columnName, String.class);
            return periodString != null ? Period.parse(periodString) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Period value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class DurationTypeResolver implements TypeResolver<Duration> {
        @Override public Class<Duration> getType() { return Duration.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Duration resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            return switch (raw) {
                case null -> null;
                case Duration d -> d;
                case String s -> Duration.parse(s);
                case Number n ->
                    // Assume milliseconds
                    Duration.ofMillis(n.longValue());
                default -> throw new IllegalArgumentException(
                    "Unsupported Duration type for column '" + columnName + "': " + raw.getClass().getName()
                );
            };

        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Duration value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class PatternTypeResolver implements TypeResolver<Pattern> {
        private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>(3);

        @Override public Class<Pattern> getType() { return Pattern.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Pattern resolve(DatabaseResult result, String columnName) {
            String patternString = result.get(columnName, String.class);
            if (patternString == null) return null;
            return CACHE.computeIfAbsent(patternString, Pattern::compile);
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Pattern value) {
            parameters.set(index, value != null ? value.pattern() : null, String.class);
        }
    }

    public static final class DateTypeResolver implements TypeResolver<Date> {
        @Override public Class<Date> getType() { return Date.class; }
        @Override public Class<Date> getDatabaseType() { return Date.class; }

        @Override
        public Date resolve(DatabaseResult result, String columnName) {
            return result.get(columnName, Date.class);
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Date value) {
            parameters.set(index, value, Date.class);
        }
    }

    public static final class TimeTypeResolver implements TypeResolver<Time> {
        @Override public Class<Time> getType() { return Time.class; }
        @Override public Class<Time> getDatabaseType() { return Time.class; }

        @Override
        public Time resolve(DatabaseResult result, String columnName) {
            return result.get(columnName, Time.class);
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Time value) {
            parameters.set(index, value, Time.class);
        }
    }

    public static final class TimestampTypeResolver implements TypeResolver<Timestamp> {
        @Override public Class<Timestamp> getType() { return Timestamp.class; }
        @Override public Class<Timestamp> getDatabaseType() { return Timestamp.class; }

        @Override
        public Timestamp resolve(DatabaseResult result, String columnName) {
            return result.get(columnName, Timestamp.class);
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Timestamp value) {
            parameters.set(index, value, Timestamp.class);
        }
    }

    public static final class LocalDateTypeResolver implements TypeResolver<LocalDate> {
        @Override public Class<LocalDate> getType() { return LocalDate.class; }
        @Override public Class<Date> getDatabaseType() { return Date.class; }

        @Override
        public @Nullable LocalDate resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            if (raw == null) return null;

            if (raw instanceof LocalDate ld) {
                return ld;
            }
            if (raw instanceof Date d) {
                return d.toLocalDate();
            }
            if (raw instanceof java.util.Date d) {
                return new Date(d.getTime()).toLocalDate();
            }
            if (raw instanceof String s) {
                return LocalDate.parse(s);
            }

            throw new IllegalArgumentException(
                "Unsupported LocalDate type for column '" + columnName + "': " + raw.getClass().getName()
            );
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, LocalDate value) {
            parameters.set(index, value != null ? Date.valueOf(value) : null, Date.class);
        }
    }

    public static final class LocalTimeTypeResolver implements TypeResolver<LocalTime> {
        @Override public Class<LocalTime> getType() { return LocalTime.class; }
        @Override public Class<Time> getDatabaseType() { return Time.class; }

        @Override
        public @Nullable LocalTime resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            return switch (raw) {
                case null -> null;
                case LocalTime lt -> lt;
                case Time t -> t.toLocalTime();
                case String s -> LocalTime.parse(s);
                default -> throw new IllegalArgumentException(
                    "Unsupported LocalTime type for column '" + columnName + "': " + raw.getClass().getName()
                );
            };

        }

        @Override
        public void insert(DatabaseParameters parameters, String index, LocalTime value) {
            parameters.set(index, value != null ? Time.valueOf(value) : null, Time.class);
        }
    }

    public static final class LocalDateTimeTypeResolver implements TypeResolver<LocalDateTime> {
        @Override public Class<LocalDateTime> getType() { return LocalDateTime.class; }
        @Override public Class<Timestamp> getDatabaseType() { return Timestamp.class; }

        @Override
        public @Nullable LocalDateTime resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            return switch (raw) {
                case null -> null;
                case LocalDateTime ldt -> ldt;
                case Timestamp ts -> ts.toLocalDateTime();
                case java.util.Date d -> new Timestamp(d.getTime()).toLocalDateTime();
                case String s -> LocalDateTime.parse(s);
                default -> throw new IllegalArgumentException(
                    "Unsupported LocalDateTime type for column '" + columnName + "': " + raw.getClass().getName()
                );
            };

        }

        @Override
        public void insert(DatabaseParameters parameters, String index, LocalDateTime value) {
            parameters.set(index, value != null ? Timestamp.valueOf(value) : null, Timestamp.class);
        }
    }

    public static final class ZonedDateTimeTypeResolver implements TypeResolver<ZonedDateTime> {
        @Override public Class<ZonedDateTime> getType() { return ZonedDateTime.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable ZonedDateTime resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? ZonedDateTime.parse(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, ZonedDateTime value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class OffsetDateTimeTypeResolver implements TypeResolver<OffsetDateTime> {
        @Override public Class<OffsetDateTime> getType() { return OffsetDateTime.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable OffsetDateTime resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? OffsetDateTime.parse(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, OffsetDateTime value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class InstantTypeResolver implements TypeResolver<Instant> {
        @Override public Class<Instant> getType() { return Instant.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable Instant resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? Instant.parse(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Instant value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class EpochInstantTypeResolver implements TypeResolver<Instant> {
        @Override public Class<Instant> getType() { return Instant.class; }
        @Override public Class<Long> getDatabaseType() { return Long.class; }

        @Override
        public @Nullable Instant resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            switch (raw) {
                case null -> {
                    return null;
                }
                case Long l -> {
                    return Instant.ofEpochMilli(l);
                }
                case Number n -> {
                    return Instant.ofEpochMilli(n.longValue());
                }
                case String s -> {
                    try {
                        return Instant.ofEpochMilli(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Cannot parse epoch millis from string: " + s, e);
                    }
                }
                default -> {
                }
            }

            throw new IllegalArgumentException(
                "Unsupported epoch instant type for column '" + columnName + "': " + raw.getClass().getName()
            );
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Instant value) {
            parameters.set(index, value != null ? value.toEpochMilli() : null, Long.class);
        }

        @Override
        public SqlEncoding getEncoding() {
            return SqlEncoding.BINARY;
        }
    }

    public static final class YearTypeResolver implements TypeResolver<Year> {
        @Override public Class<Year> getType() { return Year.class; }
        @Override public Class<Integer> getDatabaseType() { return Integer.class; }

        @Override
        public @Nullable Year resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            return switch (raw) {
                case null -> null;
                case Year y -> y;
                case Number n -> Year.of(n.intValue());
                case String s -> Year.parse(s);
                default -> throw new IllegalArgumentException(
                    "Unsupported Year type for column '" + columnName + "': " + raw.getClass().getName()
                );
            };

        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Year value) {
            parameters.set(index, value != null ? value.getValue() : null, Integer.class);
        }
    }

    public static final class MonthTypeResolver implements TypeResolver<Month> {
        @Override public Class<Month> getType() { return Month.class; }
        @Override public Class<Integer> getDatabaseType() { return Integer.class; }

        @Override
        public @Nullable Month resolve(DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            switch (raw) {
                case null -> {
                    return null;
                }
                case Month m -> {
                    return m;
                }
                case Number n -> {
                    return Month.of(n.intValue());
                }
                case String s -> {
                    try {
                        return Month.of(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        return Month.valueOf(s.toUpperCase());
                    }
                }
                default -> {
                }
            }

            throw new IllegalArgumentException(
                "Unsupported Month type for column '" + columnName + "': " + raw.getClass().getName()
            );
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Month value) {
            parameters.set(index, value != null ? value.getValue() : null, Integer.class);
        }
    }

    public static final class YearMonthTypeResolver implements TypeResolver<YearMonth> {
        @Override public Class<YearMonth> getType() { return YearMonth.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable YearMonth resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? YearMonth.parse(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, YearMonth value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class MonthDayTypeResolver implements TypeResolver<MonthDay> {
        @Override public Class<MonthDay> getType() { return MonthDay.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable MonthDay resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? MonthDay.parse(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, MonthDay value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class ZoneIdTypeResolver implements TypeResolver<ZoneId> {
        @Override public Class<ZoneId> getType() { return ZoneId.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable ZoneId resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? ZoneId.of(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, ZoneId value) {
            parameters.set(index, value != null ? value.getId() : null, String.class);
        }
    }

    public static final class TimeZoneTypeResolver implements TypeResolver<TimeZone> {
        @Override public Class<TimeZone> getType() { return TimeZone.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable TimeZone resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? TimeZone.getTimeZone(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, TimeZone value) {
            parameters.set(index, value != null ? value.getID() : null, String.class);
        }
    }

    public static final class OffsetTimeTypeResolver implements TypeResolver<OffsetTime> {
        @Override public Class<OffsetTime> getType() { return OffsetTime.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable OffsetTime resolve(DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? OffsetTime.parse(value) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, OffsetTime value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class BinaryInetAddressTypeResolver
        implements TypeResolver<InetAddress> {

        @Override public Class<InetAddress> getType() { return InetAddress.class; }
        @Override public Class<byte[]> getDatabaseType() { return byte[].class; }

        @Override
        public @Nullable InetAddress resolve(DatabaseResult result, String columnName) {
            byte[] bytes = result.get(columnName, byte[].class);
            if (bytes == null) return null;

            try {
                return InetAddress.getByAddress(bytes);
            } catch (UnknownHostException e) {
                throw new RuntimeException("Invalid binary IP address", e);
            }
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, InetAddress value) {
            parameters.set(
                index,
                value != null ? value.getAddress() : null,
                byte[].class
            );
        }

        @Override
        public SqlEncoding getEncoding() {
            return SqlEncoding.BINARY;
        }
    }

    public static final class BinaryUuidTypeResolver implements TypeResolver<UUID> {

        @Override public Class<UUID> getType() { return UUID.class; }
        @Override public Class<byte[]> getDatabaseType() { return byte[].class; }

        @Override
        public @Nullable UUID resolve(@NotNull DatabaseResult result, String columnName) {
            byte[] bytes = result.get(columnName, byte[].class);
            if (bytes == null) return null;

            ByteBuffer buf = ByteBuffer.wrap(bytes);
            long most = buf.getLong();
            long least = buf.getLong();
            return new UUID(most, least);
        }

        @Override
        public void insert(@NotNull DatabaseParameters parameters, String index, UUID value) {
            if (value == null) {
                parameters.set(index, null, byte[].class);
                return;
            }

            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.putLong(value.getMostSignificantBits());
            buf.putLong(value.getLeastSignificantBits());
            parameters.set(index, buf.array(), byte[].class);
        }

        @Override
        public SqlEncoding getEncoding() {
            return SqlEncoding.BINARY;
        }
    }

    public static final class UuidTypeResolver implements TypeResolver<UUID> {
        @Override public Class<UUID> getType() { return UUID.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable UUID resolve(@NotNull DatabaseResult result, String columnName) {
            Object raw = result.get(columnName, Object.class);
            if (raw == null) return null;

            if (raw instanceof UUID uuid) {
                return uuid;
            }
            if (raw instanceof String s) {
                return UUID.fromString(s);
            }
            if (raw instanceof byte[] bytes) {
                if (bytes.length != 16) {
                    throw new IllegalArgumentException(
                        "Invalid UUID byte[] length for column '" + columnName + "': " + bytes.length
                    );
                }
                ByteBuffer buf = ByteBuffer.wrap(bytes);
                return new UUID(buf.getLong(), buf.getLong());
            }

            if ("org.bson.types.Binary".equals(raw.getClass().getName())) {
                try {
                    byte[] bytes = (byte[]) raw.getClass().getMethod("getData").invoke(raw);
                    if (bytes == null) return null;
                    if (bytes.length != 16) {
                        throw new IllegalArgumentException(
                            "Invalid BSON Binary UUID length for column '" + columnName + "': " + bytes.length
                        );
                    }
                    ByteBuffer buf = ByteBuffer.wrap(bytes);
                    return new UUID(buf.getLong(), buf.getLong());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to read BSON Binary UUID for column '" + columnName + "'", e);
                }
            }

            throw new IllegalArgumentException(
                "Unsupported UUID database value type for column '" + columnName + "': " + raw.getClass().getName()
            );
        }

        @Override
        public void insert(@NotNull DatabaseParameters parameters, String index, UUID value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
        }
    }

    public static final class InetAddressTypeResolver implements TypeResolver<InetAddress> {
        private static final Map<String, InetAddress> CACHE = new ConcurrentHashMap<>(3);

        @Override public Class<InetAddress> getType() { return InetAddress.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable InetAddress resolve(DatabaseResult result, String columnName) {
            String hostAddress = result.get(columnName, String.class);
            if (hostAddress == null) return null;
            return CACHE.computeIfAbsent(hostAddress, s -> {
                try { return InetAddress.getByName(s); }
                catch (UnknownHostException e) { throw new RuntimeException("Invalid IP address in database: " + s, e); }
            });
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, InetAddress value) {
            parameters.set(index, value != null ? value.getHostAddress() : null, String.class);
        }
    }

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

    public record SqlTypeMapping(
        String visual,
        @Nullable String binary
    ) {
        public String resolve(SqlEncoding encoding) {
            if (encoding == SqlEncoding.BINARY && binary != null) {
                return binary;
            }
            return visual;
        }

        public static SqlTypeMapping of(String visual) {
            return new SqlTypeMapping(visual, null);
        }

        public static SqlTypeMapping of(String visual, String binary) {
            return new SqlTypeMapping(visual, binary);
        }
    }

    private record ResolverKey(Class<?> type, SqlEncoding encoding) {}
}