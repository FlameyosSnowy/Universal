package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseReader;
import io.github.flameyossnowy.universal.api.handler.DataHandler.DatabaseWriter;
import io.github.flameyossnowy.universal.api.json.DefaultJsonCodec;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.velocis.cache.algorithms.LRUCache;

import io.github.flameyossnowy.uniform.json.JsonAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.net.InetAddress;
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
import java.util.Collections;
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
    private volatile Supplier<JsonAdapter> objectMapperSupplier;

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
     * Configure the JsonAdapter supplier used to instantiate codecs that require it
     * (e.g. {@link DefaultJsonCodec}).
     */
    public void setJsonAdapterSupplier(@Nullable Supplier<JsonAdapter> objectMapperSupplier) {
        this.objectMapperSupplier = objectMapperSupplier;
    }

    /**
     * @return a JsonAdapter instance if a supplier was configured; otherwise null.
     */
    public @Nullable JsonAdapter getJsonAdapter() {
        Supplier<JsonAdapter> supplier = this.objectMapperSupplier;
        return supplier != null ? supplier.get() : null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> @NotNull JsonCodec<T> getJsonCodec(Class<? extends JsonCodec> codecClass) {
        if (codecClass == null) {
            throw new IllegalArgumentException("codecClass cannot be null");
        }

        return (JsonCodec<T>) jsonCodecs.computeIfAbsent((Class<? extends JsonCodec<?>>) codecClass, c -> {
            try {
                if (DefaultJsonCodec.class.equals(c)) {
                    Supplier<JsonAdapter> supplier = this.objectMapperSupplier;
                    if (supplier == null) {
                        throw new IllegalStateException(
                            "DefaultJsonCodec requires an JsonAdapter, but no JsonAdapter supplier was configured"
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

                // Fallback: JsonAdapter ctor if available
                Supplier<JsonAdapter> supplier = this.objectMapperSupplier;
                if (supplier != null) {
                    try {
                        Constructor<?> ctor = c.getDeclaredConstructor(JsonAdapter.class);
                        ctor.setAccessible(true);
                        return (JsonCodec<?>) ctor.newInstance(supplier.get());
                    } catch (NoSuchMethodException ignored) {
                        // fall through
                    }
                }

                throw new IllegalStateException(
                    "Cannot instantiate JsonCodec: " + c.getName() + ". Provide a public no-args constructor " +
                        "or an JsonAdapter constructor (and configure an JsonAdapter supplier)."
                );
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate JsonCodec: " + c.getName(), e);
            }
        });
    }

    /**
     * Get or create a JsonCodec using a pre-configured supplier.
     * This avoids reflection and is the preferred method for code-generated scenarios.
     *
     * @param codecClass the codec class (used as cache key)
     * @param supplier the supplier that instantiates the codec (may return null for DefaultJsonCodec)
     * @return the JsonCodec instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> @NotNull JsonCodec<T> getJsonCodecFromSupplier(
            Class<? extends JsonCodec> codecClass,
            java.util.function.Supplier<JsonCodec<?>> supplier) {
        if (codecClass == null) {
            throw new IllegalArgumentException("codecClass cannot be null");
        }

        return (JsonCodec<T>) jsonCodecs.computeIfAbsent((Class<? extends JsonCodec<?>>) codecClass, c -> {
            // First try the supplier - if it returns non-null, use it
            JsonCodec<?> supplied = supplier.get();
            if (supplied != null) {
                return supplied;
            }

            // Fall back to DefaultJsonCodec if supplier returns null
            if (DefaultJsonCodec.class.equals(c)) {
                Supplier<JsonAdapter> adapterSupplier = this.objectMapperSupplier;
                if (adapterSupplier == null) {
                    throw new IllegalStateException(
                        "DefaultJsonCodec requires a JsonAdapter, but no JsonAdapter supplier was configured"
                    );
                }
                return new DefaultJsonCodec<>(adapterSupplier.get());
            }

            // For custom codecs where supplier returned null, this is an error
            throw new IllegalStateException(
                "JsonCodec supplier returned null for: " + c.getName()
            );
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

        Object direct = resolvers.get(key);
        if (direct == NULL_MARKER) return null;
        if (direct != null) return (TypeResolver<T>) direct;

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

            if (registered.encoding() != encoding) continue;

            if (registered.type() == Enum.class) continue;

            if (registered.type().isAssignableFrom(type)) {
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

    public Map<ResolverKey, TypeResolver<?>> resolvers() {
        return Collections.unmodifiableMap(resolvers);
    }
}