package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseReader;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseWriter;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.velocis.cache.algorithms.LRUCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TypeResolverRegistry {
    private final Map<Class<?>, TypeResolver<?>> resolvers = new ConcurrentHashMap<>(24);
    private final Map<Class<?>, DataHandler<?>> dataHandlers = new ConcurrentHashMap<>(24);
    private final LRUCache<Class<?>, TypeResolver<?>> assignableCache = new LRUCache<>(32);

    private final Map<Class<?>, String> encodedTypeMappers = new ConcurrentHashMap<>(
            Map.ofEntries(
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
                    Map.entry(UUID.class, "VARCHAR(36)"),

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
                    Map.entry(Pattern.class, "VARCHAR(255)"),

                    Map.entry(Class.class, "VARCHAR(255)"),
                    Map.entry(Locale.class, "VARCHAR(255)"),
                    Map.entry(Currency.class, "VARCHAR(255)")
            )
    );

    public TypeResolverRegistry() {
        registerDefaultHandlers();
        registerDefaults();
    }

    @Nullable
    public String getType(TypeResolver<?> resolver) {
        if (resolver == null) return null;
        return getType(resolver.getType());
    }

    public String getType(@NotNull Class<?> type) {
        return encodedTypeMappers.get(type);
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
        resolvers.put(resolver.getType(), resolver);
    }

    public <T> void register(TypeResolver<T> resolver) {
        registerInternal(resolver);
        encodedTypeMappers.put(resolver.getType(), encodedTypeMappers.get(resolver.getDatabaseType()));
        assignableCache.remove(resolver.getType());
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
    public <T> TypeResolver<T> getResolver(Class<T> type) {
        return (TypeResolver<T>) resolvers.get(type);
    }

    @SuppressWarnings("unchecked")
    public <T> DataHandler<T> getHandler(Class<?> type) {
        return (DataHandler<T>) dataHandlers.get(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> @Nullable TypeResolver<T> resolve(Class<T> type) {
        var resolver = (TypeResolver<T>) resolvers.get(type);
        if (resolver != null) {
            return resolver;
        }

        var typeResolver = (TypeResolver<T>) assignableCache.get(type);
        if (typeResolver != null) {
            return typeResolver;
        }

        if (type.isEnum()) {
            // SAFETY: Raw types work fine here and are tested.
            TypeResolver<T> enumResolver = (TypeResolver<T>) TypeResolver.forEnum((Class<? extends Enum>) type);
            registerInternal(enumResolver);
            return enumResolver;
        }

        for (Map.Entry<Class<?>, TypeResolver<?>> entry : resolvers.entrySet()) {
            Class<?> registered = entry.getKey();

            if (registered.isAssignableFrom(type)) {
                TypeResolver<?> newResolver = entry.getValue();
                assignableCache.put(type, newResolver);
                return (TypeResolver<T>) newResolver;
            }
        }

        assignableCache.put(type, null);
        return null;
    }

    public boolean hasResolver(Class<?> type) {
        return resolvers.containsKey(type) || resolve(type) != null;
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
            String value = result.get(columnName, String.class);
            return value != null ? new BigInteger(value) : null;
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
        public BigDecimal resolve(DatabaseResult result, String columnName) {
            return result.get(columnName, BigDecimal.class);
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
            String durationString = result.get(columnName, String.class);
            return durationString != null ? Duration.parse(durationString) : null;
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Duration value) {
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
            Date date = result.get(columnName, Date.class);
            return date != null ? date.toLocalDate() : null;
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
            Time time = result.get(columnName, Time.class);
            return time != null ? time.toLocalTime() : null;
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
            Timestamp ts = result.get(columnName, Timestamp.class);
            return ts != null ? ts.toLocalDateTime() : null;
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

    public static final class UuidTypeResolver implements TypeResolver<UUID> {
        @Override public Class<UUID> getType() { return UUID.class; }
        @Override public Class<String> getDatabaseType() { return String.class; }

        @Override
        public @Nullable UUID resolve(@NotNull DatabaseResult result, String columnName) {
            String value = result.get(columnName, String.class);
            return value != null ? UUID.fromString(value) : null;
        }

        @Override
        public void insert(@NotNull DatabaseParameters parameters, String index, UUID value) {
            parameters.set(index, value != null ? value.toString() : null, String.class);
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
}