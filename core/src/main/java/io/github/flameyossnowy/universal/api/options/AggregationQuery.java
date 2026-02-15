package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Query for aggregation operations with GROUP BY and HAVING clauses.
 *
 * <p>Examples:
 * <pre>{@code
 * // Count by status
 * Query.aggregate()
 *     .select(
 *         field("status"),
 *         field("id").count().as("count")
 *     )
 *     .groupBy("status")
 *     .build();
 * 
 * // Conditional counts
 * Query.aggregate()
 *     .select(
 *         field("status").countIf(eq("active")).as("active_count"),
 *         field("status").countIf(eq("inactive")).as("inactive_count")
 *     )
 *     .build();
 * 
 * // With HAVING clause
 * Query.aggregate()
 *     .select(
 *         field("departmentId"),
 *         field("id").count().as("emp_count")
 *     )
 *     .groupBy("departmentId")
 *     .having(field("id").count().gt(10))
 *     .build();
 * 
 * // Distinct count in subquery
 * Query.aggregate()
 *     .select(
 *         field("name"),
 *         subQuery(Employee.class)
 *             .select(field("role").countDistinct())
 *             .where("departmentId").eq(field("id"))
 *             .as("distinct_roles")
 *     )
 *     .from(Department.class)
 *     .build();
 * }</pre>
 */
public record AggregationQuery(
    List<FieldDefinition> selectFields,
    @Nullable Class<?> fromTable,
    List<FilterOption> whereFilters,
    List<String> groupByFields,
    List<FilterOption> havingFilters,
    List<SortOption> orderBy,
    int limit
) implements Query {

    public static class AggregationQueryBuilder implements Filterable {
        private final List<FieldDefinition> selectFields = new ArrayList<>();
        private final List<FilterOption> whereFilters = new ArrayList<>();
        private final List<String> groupByFields = new ArrayList<>();
        private final List<FilterOption> havingFilters = new ArrayList<>();
        private final List<SortOption> orderBy = new ArrayList<>();
        private Class<?> fromTable;
        private int limit = -1;

        /**
         * Add fields to SELECT clause.
         * 
         * <pre>{@code
         * .select(
         *     field("departmentId"),
         *     field("id").count().as("emp_count"),
         *     field("salary").avg().as("avg_salary")
         * )
         * }</pre>
         */
        public AggregationQueryBuilder select(FieldDefinition... fields) {
            selectFields.addAll(List.of(fields));
            return this;
        }

        /**
         * Convenience method to select a field without aggregation.
         */
        public AggregationQueryBuilder select(String field) {
            selectFields.add(new SimpleFieldDefinition(field, null));
            return this;
        }

        /**
         * Specify the source table (useful for subqueries).
         */
        public AggregationQueryBuilder from(Class<?> table) {
            this.fromTable = table;
            return this;
        }

        /**
         * Add WHERE filters (applied before grouping).
         */
        public QueryField<AggregationQueryBuilder> where(String field) {
            return new QueryField<>(this, field);
        }

        public QueryField<AggregationQueryBuilder> whereJson(String field, String jsonPath) {
            return new QueryField<>(this, field, jsonPath);
        }

        /**
         * Add GROUP BY fields.
         * 
         * <pre>{@code
         * .groupBy("departmentId", "locationId")
         * }</pre>
         */
        public AggregationQueryBuilder groupBy(String... fields) {
            groupByFields.addAll(List.of(fields));
            return this;
        }

        /**
         * Add HAVING filters (applied after grouping).
         * 
         * <pre>{@code
         * .having(field("id").count().gt(10))
         * }</pre>
         */
        public HavingClause having() {
            return new HavingClause(this);
        }

        void addHavingFilter(FilterOption filter) {
            havingFilters.add(filter);
        }

        /**
         * Add ORDER BY clause.
         */
        public AggregationQueryBuilder orderBy(String field, SortOrder direction) {
            orderBy.add(new SortOption(field, direction));
            return this;
        }

        /**
         * Limit results.
         */
        public AggregationQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        @Override
        public void addFilter(FilterOption filter) {
            whereFilters.add(filter);
        }

        public AggregationQuery build() {
            return new AggregationQuery(
                new ArrayList<>(selectFields),
                fromTable,
                new ArrayList<>(whereFilters),
                new ArrayList<>(groupByFields),
                new ArrayList<>(havingFilters),
                new ArrayList<>(orderBy),
                limit
            );
        }
    }

    /**
     * Special builder for HAVING clause to support aggregate comparisons.
     */
    public static class HavingClause {
        private final AggregationQueryBuilder parent;

        HavingClause(AggregationQueryBuilder parent) {
            this.parent = parent;
        }

        /**
         * Start a HAVING condition on a field.
         * 
         * <pre>{@code
         * .having().field("id").count().gt(10)
         * }</pre>
         */
        public HavingQueryField field(String fieldName) {
            return new HavingQueryField(parent, fieldName);
        }
    }

    /**
     * Special QueryField for HAVING clause that adds to having filters.
     */
    public static class HavingQueryField extends QueryField<HavingFilterable> {
        public HavingQueryField(AggregationQueryBuilder builder, String field) {
            super(new HavingFilterable(builder), field);
        }
    }

    /**
     * Filterable wrapper that adds to HAVING instead of WHERE.
     */
    private record HavingFilterable(AggregationQueryBuilder parent) implements Filterable {
        @Override
        public void addFilter(FilterOption filter) {
            parent.addHavingFilter(filter);
        }
    }
}