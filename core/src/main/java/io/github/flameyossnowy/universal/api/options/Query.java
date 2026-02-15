package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating various query types with convenient static methods.
 */
public sealed interface Query permits AggregationQuery, DeleteQuery, SelectQuery, UpdateQuery, WindowQuery {

    /**
     * Start building a SELECT query.
     */
    static SelectQuery.SelectQueryBuilder select(String... columns) {
        return new SelectQuery.SelectQueryBuilder(columns);
    }

    /**
     * Start building an UPDATE query.
     */
    static UpdateQuery.UpdateQueryBuilder update() {
        return new UpdateQuery.UpdateQueryBuilder();
    }

    /**
     * Start building a DELETE query.
     */
    static DeleteQuery.DeleteQueryBuilder delete() {
        return new DeleteQuery.DeleteQueryBuilder();
    }

    /**
     * Start building an aggregation query with GROUP BY.
     */
    static AggregationQuery.AggregationQueryBuilder aggregate() {
        return new AggregationQuery.AggregationQueryBuilder();
    }

    /**
     * Create a subquery.
     */
    static SubQuery.SubQueryBuilder subQuery(@NotNull Class<?> entityClass) {
        return new SubQuery.SubQueryBuilder(entityClass);
    }

    /**
     * Create a field reference for aggregations.
     */
    static <B extends Filterable> QueryField<B> field(String fieldName) {
        return new QueryField<>(null, fieldName);
    }

    /**
     * Create a JSON field reference.
     */
    static <B extends Filterable> QueryField<B> jsonField(String fieldName, String jsonPath) {
        return new QueryField<>(null, fieldName, jsonPath);
    }

    /**
     * Create an equality filter (for use in countIf, sumIf, etc.).
     */
    static FilterOption eq(Object value) {
        return new SelectOption("", "=", value);
    }

    static FilterOption ne(Object value) {
        return new SelectOption("", "!=", value);
    }

    static FilterOption gt(Object value) {
        return new SelectOption("", ">", value);
    }

    static FilterOption gte(Object value) {
        return new SelectOption("", ">=", value);
    }

    static FilterOption lt(Object value) {
        return new SelectOption("", "<", value);
    }

    static FilterOption lte(Object value) {
        return new SelectOption("", "<=", value);
    }

    /**
     * Create a window function query (for databases that support window functions).
     */
    static WindowQuery.WindowQueryBuilder window() {
        return new WindowQuery.WindowQueryBuilder();
    }
}