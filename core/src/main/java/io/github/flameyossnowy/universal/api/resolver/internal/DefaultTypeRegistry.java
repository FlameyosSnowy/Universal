package io.github.flameyossnowy.universal.api.resolver.internal;

import io.github.flameyossnowy.universal.api.resolver.SqlTypeMapping;
import io.github.flameyossnowy.universal.api.resolver.TypeRegistration;
import io.github.flameyossnowy.universal.api.resolver.TypeRegistry;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Internal implementation of {@link TypeRegistry} that wraps a {@link TypeResolverRegistry}.
 *
 * <p>This class provides the database-agnostic facade over the internal type resolver registry,
 * hiding implementation details while still allowing flexible type configuration.</p>
 */
public final class DefaultTypeRegistry implements TypeRegistry {
    private final TypeResolverRegistry internalRegistry;

    public DefaultTypeRegistry(@NotNull TypeResolverRegistry internalRegistry) {
        this.internalRegistry = Objects.requireNonNull(internalRegistry, "Internal registry cannot be null");
    }

    @Override
    @NotNull
    public TypeMappingBuilder mapType(@NotNull Class<?> javaType) {
        return new DefaultTypeMappingBuilder(this, javaType);
    }

    @Override
    public <T> void addResolver(@NotNull TypeResolver<T> resolver) {
        internalRegistry.register(resolver);
    }

    @Override
    public <E extends Enum<E>> void registerEnum(@NotNull Class<E> enumClass) {
        internalRegistry.registerEnum(enumClass);
    }

    @Override
    public <E extends Enum<E>> void registerEnum(@NotNull Class<E> enumClass, @NotNull String databaseTypeName) {
        SqlTypeMapping mapping = SqlTypeMapping.enumBuilder()
            .withDatabaseSpecific(databaseTypeName, databaseTypeName)
            .build();
        internalRegistry.registerEnum(enumClass, mapping);
    }

    /**
     * Combines multiple type registrations into a single registration.
     *
     * @param registrations the list of registrations to combine
     * @return a combined registration, or null if the list is empty
     */
    @Nullable
    public static TypeRegistration combineRegistrations(@NotNull java.util.List<TypeRegistration> registrations) {
        if (registrations.isEmpty()) {
            return null;
        }
        return registry -> {
            for (TypeRegistration reg : registrations) {
                reg.register(registry);
            }
        };
    }

    /**
     * Internal implementation of TypeMappingBuilder.
     */
    private static final class DefaultTypeMappingBuilder implements TypeMappingBuilder {
        private final DefaultTypeRegistry registry;
        private final Class<?> javaType;
        private String visualType;
        private String binaryType;
        private final Map<String, String> dialectSpecificTypes = new HashMap<>();

        DefaultTypeMappingBuilder(DefaultTypeRegistry registry, Class<?> javaType) {
            this.registry = registry;
            this.javaType = javaType;
        }

        @Override
        @NotNull
        public TypeMappingBuilder toVisual(@NotNull String sqlType) {
            this.visualType = sqlType;
            return this;
        }

        @Override
        @NotNull
        public TypeMappingBuilder toBinary(@NotNull String sqlType) {
            this.binaryType = sqlType;
            return this;
        }

        @Override
        @NotNull
        public TypeMappingBuilder forDialect(@NotNull String dialect, @NotNull String sqlType) {
            this.dialectSpecificTypes.put(dialect.toLowerCase(), sqlType);
            return this;
        }

        @Override
        public void register() {
            SqlTypeMapping.Builder builder = SqlTypeMapping.builder();

            if (visualType != null) {
                builder.visual(visualType);
            }
            if (binaryType != null) {
                builder.binary(binaryType);
            }

            dialectSpecificTypes.forEach(builder::withDatabaseSpecific);

            registry.internalRegistry.registerSqlTypeMapping(javaType, builder.build());
        }
    }
}
