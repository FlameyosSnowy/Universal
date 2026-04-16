package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.uniform.json.dom.JsonNumber;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import io.github.flameyossnowy.uniform.json.dom.JsonBoolean;
import io.github.flameyossnowy.uniform.json.dom.JsonDouble;
import io.github.flameyossnowy.uniform.json.dom.JsonInteger;
import io.github.flameyossnowy.uniform.json.dom.JsonLong;
import io.github.flameyossnowy.uniform.json.dom.JsonNull;
import io.github.flameyossnowy.uniform.json.dom.JsonString;
import io.github.flameyossnowy.uniform.json.dom.JsonValue;

import io.github.flameyossnowy.uniform.json.resolvers.CoreTypeResolver;
import io.github.flameyossnowy.uniform.json.resolvers.CoreTypeResolverRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.HashSet;

/**
 * Bridges {@link TypeResolver} instances from the Universal RDOR into
 * {@link CoreTypeResolver} instances usable by {@code JsonAdapter}.
 *
 * <p>Resolvers whose type is already natively handled by
 * {@link CoreTypeResolverRegistry} are silently skipped - there is no point
 * wrapping them since the built-in implementation is already optimal.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Bridge everything from a TypeResolverRegistry at once:
 * TypeResolverBridge.registerAll(universalRegistry, CoreTypeResolverRegistry.INSTANCE);
 *
 * // Or bridge a single resolver:
 * TypeResolverBridge.register(myTypeResolver, CoreTypeResolverRegistry.INSTANCE);
 * }</pre>
 *
 * <h3>Serialize direction (T -> JsonValue)</h3>
 * Uses a {@link MockDatabaseParameters} to capture the value that
 * {@link TypeResolver#insert} writes, then converts the stored object to the
 * appropriate {@link JsonValue} based on {@link TypeResolver#getDatabaseType()}.
 *
 * <h3>Deserialize direction (JsonValue -> T)</h3>
 * Converts the {@link JsonValue} to the resolver's database type, wraps it in a
 * {@link MockDatabaseResult}, then delegates to {@link TypeResolver#resolve}.
 */
public final class TypeResolverBridge {

    private TypeResolverBridge() {}

    private static final Set<Class<?>> BUILT_IN = Set.of(
        // Primitives & wrappers
        String.class,
        Integer.class,  int.class,
        Long.class,     long.class,
        Double.class,   double.class,
        Float.class,    float.class,
        Short.class,    short.class,
        Byte.class,     byte.class,
        Boolean.class,  boolean.class,
        Character.class, char.class,
        // Big numbers
        BigInteger.class, BigDecimal.class,
        // Common types
        UUID.class, URI.class, URL.class, Path.class,
        // Java Time
        LocalDate.class, LocalTime.class, LocalDateTime.class,
        ZonedDateTime.class, OffsetDateTime.class, OffsetTime.class,
        Instant.class, Duration.class, Period.class,
        Year.class, YearMonth.class, MonthDay.class, Month.class,
        ZoneId.class, TimeZone.class
    );

    /**
     * Iterates all resolvers in {@code source} and registers each non-duplicate
     * one as a {@link CoreTypeResolver} in {@code target}.
     *
     * @param source the Universal {@link TypeResolverRegistry} to read from
     * @param target the {@link CoreTypeResolverRegistry} to register into
     */
    public static void registerAll(
            @NotNull TypeResolverRegistry source,
            @NotNull CoreTypeResolverRegistry target) {
        // TypeResolverRegistry stores VISUAL and BINARY encodings as separate entries
        // for the same type (e.g. UUID, Instant, InetAddress). We only want one
        // CoreTypeResolver per type, so track which types we've already registered.
        Set<Class<?>> registered = new HashSet<>();
        for (TypeResolver<?> resolver : source.resolvers().values()) {
            if (registered.add(resolver.getType())) {
                registerIfAbsent(resolver, target);
            }
        }
    }

    /**
     * Wraps a single {@link TypeResolver} as a {@link CoreTypeResolver} and
     * registers it in {@code target}, unless the type is already built-in.
     *
     * @param resolver the Universal resolver to wrap
     * @param target   the {@link CoreTypeResolverRegistry} to register into
     * @return {@code true} if registered, {@code false} if skipped as a duplicate
     */
    public static <T> boolean register(
            @NotNull TypeResolver<T> resolver,
            @NotNull CoreTypeResolverRegistry target) {
        return registerIfAbsent(resolver, target);
    }

    private static <T> boolean registerIfAbsent(
            TypeResolver<T> resolver,
            CoreTypeResolverRegistry target) {

        Class<T> type = resolver.getType();

        // Skip null types (can happen with some internal sentinel resolvers)
        if (type == null) return false;

        // Skip anything CoreTypeResolverRegistry already handles natively
        if (BUILT_IN.contains(type)) return false;

        // Skip enums - CoreTypeResolverRegistry handles them via assignable scan
        if (type.isEnum()) return false;

        // Skip if already explicitly registered (don't override user-registered resolvers)
        if (target.has(type)) return false;

        target.register(new BridgedTypeResolver<>(resolver));
        return true;
    }

    private record BridgedTypeResolver<T>(TypeResolver<T> delegate) implements CoreTypeResolver<T> {

        private static final String COL = "v";

        @Override
        public @NotNull Class<T> getType() {
            return delegate.getType();
        }


        @Override
        public @Nullable T deserialize(@NotNull JsonValue value) {
            if (value instanceof JsonNull) return null;

            Object dbValue = toDbValue(value, delegate.getDatabaseType());
            return delegate.resolve(new MockDatabaseResult(dbValue), COL);
        }

        @Override
        public @NotNull JsonValue serialize(@NotNull T value) {
            MockDatabaseParameters params = new MockDatabaseParameters();
            delegate.insert(params, COL, value);
            return toJsonValue(params.value, delegate.getDatabaseType());
        }

        private static @Nullable Object toDbValue(@NotNull JsonValue v, @NotNull Class<?> dbType) {
            if (dbType == String.class)
                return v instanceof JsonString(String value) ? value : v.toString();

            if (dbType == Long.class || dbType == long.class)
                return v instanceof JsonNumber n ? n.longValue() : Long.parseLong(v.toString());

            if (dbType == Integer.class || dbType == int.class)
                return v instanceof JsonNumber n ? n.intValue() : Integer.parseInt(v.toString());

            if (dbType == Double.class || dbType == double.class)
                return v instanceof JsonNumber n ? n.doubleValue() : Double.parseDouble(v.toString());

            if (dbType == Float.class || dbType == float.class)
                return v instanceof JsonNumber n ? n.floatValue() : Float.parseFloat(v.toString());

            if (dbType == Boolean.class || dbType == boolean.class)
                return v instanceof JsonBoolean(boolean value) ? value : Boolean.parseBoolean(v.toString());

            if (dbType == byte[].class)
                return v instanceof JsonString(String value) ? Base64.getDecoder().decode(value) : null;

            // Fallback - pass the raw toString and let the TypeResolver sort it out
            return v.toString();
        }

        private static @NotNull JsonValue toJsonValue(@Nullable Object dbValue, @NotNull Class<?> dbType) {
            if (dbValue == null) return JsonNull.INSTANCE;

            if (dbType == String.class) return new JsonString((String) dbValue);
            if (dbType == Long.class || dbType == long.class) return new JsonLong((Long) dbValue);
            if (dbType == Integer.class || dbType == int.class) return new JsonInteger((Integer) dbValue);
            if (dbType == Double.class || dbType == double.class) return new JsonDouble((Double) dbValue);
            if (dbType == Float.class || dbType == float.class) return new JsonDouble(((Float) dbValue).doubleValue());
            if (dbType == Boolean.class || dbType == boolean.class) return JsonBoolean.of((Boolean) dbValue);
            if (dbType == byte[].class) return new JsonString(Base64.getEncoder().encodeToString((byte[]) dbValue));

            // Fallback - toString serialization for unknown DB types
            return new JsonString(dbValue.toString());
        }
    }

    private static final class MockDatabaseParameters implements DatabaseParameters {

        @Nullable Object value;

        @Override public String getAdapterType()        { return "json-bridge"; }
        @Override public boolean supportsArraysNatively() { return false; }
        @Override public io.github.flameyossnowy.universal.api.handler.CollectionHandler getCollectionHandler() { return null; }
        @Override public int size()                     { return value != null ? 1 : 0; }

        @Override
        public <T> void set(@NotNull String name, @Nullable T v, @NotNull Class<?> type) {
            this.value = v;
        }

        @Override
        public <T> void setRaw(@NotNull String name, @Nullable T v, @NotNull Class<?> type) {
            this.value = v;
        }

        @Override
        public void setNull(@NotNull String name, @NotNull Class<?> type) {
            this.value = null;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> @Nullable T get(int index, @NotNull Class<T> type) { return (T) value; }

        @Override @SuppressWarnings("unchecked")
        public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) { return (T) value; }

        @Override
        public boolean contains(@NotNull String name) { return value != null; }
    }

    private record MockDatabaseResult(@Nullable Object value) implements DatabaseResult {

        private static final String COL = "v";

        @Override
        public CollectionHandler getCollectionHandler() {
            return null;
        }

        @Override
        public boolean supportsArraysNatively() {
            return false;
        }

        @Override
        public RepositoryModel<?, ?> repositoryModel() {
            return null;
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getColumnName(int index) {
            return COL;
        }

        @Override
        public boolean hasColumn(String col) {
            return COL.equals(col);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T get(String col, Class<T> type) {
            if (!COL.equals(col)) return null;
            return (T) value;
        }
    }
}