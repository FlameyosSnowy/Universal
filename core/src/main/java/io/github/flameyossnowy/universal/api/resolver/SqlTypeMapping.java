package io.github.flameyossnowy.universal.api.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines SQL type mappings for a Java type, with support for database-specific overrides.
 *
 * <p>Provides three levels of type resolution:
 * <ul>
 *   <li><b>Visual</b> - Default text-based representation (e.g., VARCHAR(36) for UUID)</li>
 *   <li><b>Binary</b> - Binary representation if supported (e.g., BINARY(16) for UUID)</li>
 *   <li><b>Database-specific</b> - Native types for specific databases (e.g., PostgreSQL's uuid type)</li>
 * </ul>
 *
 * <p>Example: PostgreSQL UUID support
 * <pre>{@code
 * SqlTypeMapping.uuidBuilder()
 *     .visual("VARCHAR(36)")
 *     .binary("BINARY(16)")
 *     .withDatabaseSpecific(SqlType.POSTGRESQL, "uuid")
 *     .build();
 * }</pre>
 */
public final class SqlTypeMapping {
    private final String visual;
    private final @Nullable String binary;
    private final Map<String, String> databaseSpecificTypes;

    private SqlTypeMapping(String visual, @Nullable String binary, Map<String, String> databaseSpecificTypes) {
        this.visual = visual;
        this.binary = binary;
        this.databaseSpecificTypes = databaseSpecificTypes != null ? databaseSpecificTypes : Collections.emptyMap();
    }

    /**
     * Resolves the SQL type for the given encoding, without database-specific override.
     *
     * @param encoding the encoding type
     * @return the SQL type string
     */
    public String resolve(SqlEncoding encoding) {
        return resolve(encoding, null);
    }

    /**
     * Resolves the SQL type considering both encoding and database dialect.
     *
     * @param encoding the encoding type (visual or binary)
     * @param databaseType the database type identifier (e.g., "postgresql", "mysql", "sqlite"), or null
     * @return the SQL type string
     */
    public String resolve(SqlEncoding encoding, @Nullable String databaseType) {
        // Check database-specific mapping first (case-insensitive)
        if (databaseType != null) {
            String specific = databaseSpecificTypes.get(databaseType.toLowerCase());
            if (specific != null) {
                return specific;
            }
        }

        // Fall back to encoding-based resolution
        if (encoding == SqlEncoding.BINARY && binary != null) {
            return binary;
        }
        return visual;
    }

    public String visual() {
        return visual;
    }

    public @Nullable String binary() {
        return binary;
    }

    public Map<String, String> databaseSpecificTypes() {
        return Collections.unmodifiableMap(databaseSpecificTypes);
    }

    public static SqlTypeMapping of(String visual) {
        return new SqlTypeMapping(visual, null, null);
    }

    public static SqlTypeMapping of(String visual, String binary) {
        return new SqlTypeMapping(visual, binary, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder uuidBuilder() {
        return new Builder().visual("VARCHAR(36)").binary("BINARY(16)");
    }

    public static Builder enumBuilder() {
        return new Builder().visual("VARCHAR(64)");
    }

    /**
     * Builder for creating SqlTypeMapping with database-specific overrides.
     */
    public static class Builder {
        private String visual;
        private @Nullable String binary;
        private Map<String, String> databaseSpecificTypes = new HashMap<>();

        public Builder visual(String visual) {
            this.visual = Objects.requireNonNull(visual, "visual type cannot be null");
            return this;
        }

        public Builder binary(@Nullable String binary) {
            this.binary = binary;
            return this;
        }

        /**
         * Adds a database-specific type mapping.
         *
         * @param databaseType the database type (e.g., "postgresql", "mysql", "sqlite")
         * @param sqlType the SQL type for this database (e.g., "uuid", "float8", "ENUM")
         * @return this builder
         */
        public Builder withDatabaseSpecific(@NotNull String databaseType, @NotNull String sqlType) {
            databaseSpecificTypes.put(databaseType.toLowerCase(), sqlType);
            return this;
        }

        /**
         * Adds a database-specific type mapping using the SQLType enum.
         *
         * @param databaseType the database implementation type
         * @param sqlType the SQL type for this database
         * @return this builder
         */
        public Builder withDatabaseSpecific(@NotNull DatabaseDialect databaseType, @NotNull String sqlType) {
            databaseSpecificTypes.put(databaseType.getIdentifier(), sqlType);
            return this;
        }

        public SqlTypeMapping build() {
            if (visual == null) {
                throw new IllegalStateException("visual type must be specified");
            }
            return new SqlTypeMapping(visual, binary, databaseSpecificTypes.isEmpty() ? null : databaseSpecificTypes);
        }
    }
}
