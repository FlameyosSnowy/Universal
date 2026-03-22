package io.github.flameyossnowy.universal.sql.internals.query;

import java.util.List;

/**
 * Bundles a SQL string together with the ordered list of parameter names that
 * correspond to each {@code ?} placeholder.  The i-th entry in
 * {@code parameterNames} maps to the (i+1)-th bind position (1-based JDBC
 * indexing).
 *
 * <p>Replacing the raw {@code String} return type with this record lets
 * {@link io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters}
 * build its name -> index map directly from builder output, removing the need for
 * any SQL tokenisation / parsing at runtime.
 */
public record ParameterizedSql(String sql, List<String> parameterNames) {

    /** Convenience factory when there are no bind parameters (e.g. SELECT *). */
    public static ParameterizedSql of(String sql) {
        return new ParameterizedSql(sql, List.of());
    }

    public static ParameterizedSql of(String sql, List<String> names) {
        return new ParameterizedSql(sql, List.copyOf(names));
    }
}