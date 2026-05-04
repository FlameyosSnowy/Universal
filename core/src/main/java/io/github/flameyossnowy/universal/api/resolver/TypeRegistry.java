package io.github.flameyossnowy.universal.api.resolver;

import org.jetbrains.annotations.NotNull;

/**
 * A database-agnostic registry for custom type mappings and resolvers.
 *
 * <p>This interface provides a clean, hygienic API for registering custom types
 * without requiring direct interaction with the internal {@link TypeResolverRegistry}.
 * It abstracts away the database-specific details while still allowing flexible
 * type configuration.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * builder.registerTypes(registry -> {
 *     // Register a custom type mapping (database-agnostic)
 *     registry.mapType(MyCustomType.class)
 *         .toVisual("TEXT")
 *         .toBinary("BLOB")
 *         .register();
 *
 *     // Register a custom type resolver
 *     registry.addResolver(new MyCustomTypeResolver());
 *
 *     // Register an enum with default string mapping
 *     registry.registerEnum(Status.class);
 * })
 * }</pre>
 *
 * @see TypeRegistration
 */
public interface TypeRegistry {

    /**
     * Starts building a type mapping for the given Java class.
     *
     * @param javaType the Java class to map
     * @return a builder for configuring the type mapping
     */
    @NotNull
    TypeMappingBuilder mapType(@NotNull Class<?> javaType);

    /**
     * Adds a custom type resolver to this registry.
     *
     * @param resolver the type resolver to add
     * @param <T> the type being resolved
     */
    <T> void addResolver(@NotNull TypeResolver<T> resolver);

    /**
     * Registers an enum type with the default string mapping.
     *
     * @param enumClass the enum class to register
     * @param <E> the enum type
     */
    <E extends Enum<E>> void registerEnum(@NotNull Class<E> enumClass);

    /**
     * Registers an enum type with a custom database type name.
     *
     * <p>For databases that support native enums (like PostgreSQL), this will
     * use the provided type name. For other databases, it will fall back to
     * string storage.</p>
     *
     * @param enumClass the enum class to register
     * @param databaseTypeName the database-specific type name (e.g., "status_type" for PostgreSQL)
     * @param <E> the enum type
     */
    <E extends Enum<E>> void registerEnum(@NotNull Class<E> enumClass, @NotNull String databaseTypeName);

    /**
     * Builder interface for configuring type mappings in a fluent API style.
     */
    interface TypeMappingBuilder {

        /**
         * Sets the visual (text) SQL type for databases that store this type as text.
         *
         * @param sqlType the SQL type string (e.g., "TEXT", "VARCHAR(255)")
         * @return this builder for chaining
         */
        @NotNull
        TypeMappingBuilder toVisual(@NotNull String sqlType);

        /**
         * Sets the binary SQL type for databases that store this type as binary.
         *
         * @param sqlType the SQL type string (e.g., "BLOB", "BINARY(16)")
         * @return this builder for chaining
         */
        @NotNull
        TypeMappingBuilder toBinary(@NotNull String sqlType);

        /**
         * Sets a database-specific SQL type for the given dialect.
         *
         * @param dialect the database dialect identifier (e.g., "postgresql", "mysql", "sqlite")
         * @param sqlType the SQL type string for this dialect
         * @return this builder for chaining
         */
        @NotNull
        TypeMappingBuilder forDialect(@NotNull String dialect, @NotNull String sqlType);

        /**
         * Registers the configured type mapping.
         */
        void register();
    }
}
