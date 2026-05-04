package io.github.flameyossnowy.universal.api.resolver;

import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for registering types with a {@link TypeRegistry}.
 *
 * <p>This interface allows custom type mappings to be configured at the builder level
 * before the repository adapter is created, using a clean, database-agnostic API.</p>
 *
 * <p>Example usage with a builder:</p>
 * <pre>{@code
 * var adapter = PostgreSQLRepositoryAdapterBuilder.of(Entity.class, Long.class)
 *     .withCredentials(credentials)
 *     .registerTypes(registry -> {
 *         // Register custom SQL type mapping
 *         registry.mapType(MyCustomType.class)
 *             .toVisual("TEXT")
 *             .forDialect("postgresql", "jsonb")
 *             .register();
 *
 *         // Register custom enum with database-specific type
 *         registry.registerEnum(Status.class, "status_type");
 *
 *         // Register custom resolver
 *         registry.addResolver(new MyCustomTypeResolver());
 *     })
 *     .build();
 * }</pre>
 *
 * @see TypeRegistry
 */
@FunctionalInterface
public interface TypeRegistration {

    /**
     * Registers custom types and mappings with the provided registry.
     *
     * @param registry the type registry to configure
     */
    void register(@NotNull TypeRegistry registry);
}
