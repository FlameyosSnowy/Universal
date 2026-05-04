package io.github.flameyossnowy.universal.api.resolver;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a database dialect for type mapping purposes.
 * Implementations provide database-specific identifiers for type resolution.
 */
public interface DatabaseDialect {

    /**
     * Gets the unique identifier for this database dialect.
     * This identifier is used as a key in database-specific type mappings.
     *
     * @return the database identifier (e.g., "postgresql", "mysql", "sqlite")
     */
    @NotNull String getIdentifier();

    /**
     * Gets the human-readable name of this database dialect.
     *
     * @return the database name
     */
    @NotNull String getName();
}
