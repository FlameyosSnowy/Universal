package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a subquery that can be used in WHERE, SELECT, or FROM clauses.
 *
 * <p>Examples:
 * <pre>{@code
 * // IN subquery
 * query.where("userId").in(
 *     subQuery(User.class)
 *         .select("id")
 *         .where("active").eq(true)
 * )
 * 
 * // Scalar subquery in SELECT
 * query.select(
 *     field("name"),
 *     subQuery(Task.class)
 *         .select(field("id").count())
 *         .where("employeeId").eq(field("id"))
 *         .as("task_count")
 * )
 * 
 * // EXISTS subquery
 * query.where("id").exists(
 *     subQuery(Order.class)
 *         .select("customerId")
 *         .where("status").eq("pending")
 * )
 * 
 * // Subquery in FROM clause (derived table)
 * Query.select()
 *     .from(
 *         subQuery(Employee.class)
 *             .select(
 *                 field("departmentId"),
 *                 field("id").count().as("emp_count")
 *             )
 *             .groupBy("departmentId")
 *             .having(field("id").count().gt(10))
 *             .as("dept_stats")
 *     )
 *     .select(field("emp_count").count())
 *     .build();
 * }</pre>
 */
public record SubQuery(
    @NotNull Class<?> entityClass,
    @NotNull List<FieldDefinition> selectFields,
    @NotNull List<FilterOption> whereFilters,
    @NotNull List<String> groupByFields,
    @NotNull List<FilterOption> havingFilters,
    @NotNull List<SortOption> orderBy,
    int limit,
    @Nullable String alias,
    @Nullable SubQuery fromSubQuery
) {

    public static class SubQueryBuilder implements Filterable {
        private final Class<?> entityClass;
        private final List<FieldDefinition> selectFields = new ArrayList<>();
        private final List<FilterOption> whereFilters = new ArrayList<>();
        private final List<String> groupByFields = new ArrayList<>();
        private final List<FilterOption> havingFilters = new ArrayList<>();
        private final List<SortOption> orderBy = new ArrayList<>();
        private SubQuery fromSubQuery;
        private int limit = -1;
        private String alias;

        public SubQueryBuilder(@NotNull Class<?> entityClass) {
            this.entityClass = entityClass;
        }

        /**
         * Select specific fields.
         * 
         * <pre>{@code
         * subQuery(User.class)
         *     .select(field("id"), field("name"))
         * }</pre>
         */
        public SubQueryBuilder select(FieldDefinition... fields) {
            selectFields.addAll(List.of(fields));
            return this;
        }

        /**
         * Select a single field by name.
         * 
         * <pre>{@code
         * subQuery(User.class)
         *     .select("id")
         * }</pre>
         */
        public SubQueryBuilder select(String field) {
            selectFields.add(new SimpleFieldDefinition(field, null));
            return this;
        }

        /**
         * Add WHERE clause.
         */
        public QueryField<SubQueryBuilder> where(String field) {
            return new QueryField<>(this, field);
        }

        public QueryField<SubQueryBuilder> whereJson(String field, String jsonPath) {
            return new QueryField<>(this, field, jsonPath);
        }

        /**
         * Reference a field from the outer query (correlated subquery).
         * 
         * <pre>{@code
         * subQuery(Task.class)
         *     .select(field("id").count())
         *     .where("employeeId").eq(outerField("id"))
         * }</pre>
         */
        public OuterFieldReference outerField(String fieldName) {
            return new OuterFieldReference(fieldName);
        }

        /**
         * Add GROUP BY clause.
         */
        public SubQueryBuilder groupBy(String... fields) {
            groupByFields.addAll(List.of(fields));
            return this;
        }

        /**
         * Add HAVING clause.
         */
        public QueryField<HavingFilterable> having(String field) {
            return new QueryField<>(new HavingFilterable(this), field);
        }

        void addHavingFilter(FilterOption filter) {
            havingFilters.add(filter);
        }

        /**
         * Add ORDER BY clause.
         */
        public SubQueryBuilder orderBy(String field, SortOrder direction) {
            orderBy.add(new SortOption(field, direction));
            return this;
        }

        /**
         * Limit results.
         */
        public SubQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Use a subquery in the FROM clause (derived table).
         */
        public SubQueryBuilder from(SubQuery subQuery) {
            this.fromSubQuery = subQuery;
            return this;
        }

        /**
         * Assign an alias to this subquery (required for derived tables).
         * 
         * <pre>{@code
         * subQuery(Employee.class)
         *     .select(field("departmentId"), field("id").count().as("emp_count"))
         *     .groupBy("departmentId")
         *     .as("dept_stats")
         * }</pre>
         */
        public SubQueryFieldDefinition as(String alias) {
            this.alias = alias;
            return new SubQueryFieldDefinition(build(), alias);
        }

        public SubQuery build() {
            return new SubQuery(
                entityClass,
                new ArrayList<>(selectFields),
                new ArrayList<>(whereFilters),
                new ArrayList<>(groupByFields),
                new ArrayList<>(havingFilters),
                new ArrayList<>(orderBy),
                limit,
                alias,
                fromSubQuery
            );
        }

        @Override
        public void addFilter(FilterOption filter) {
            whereFilters.add(filter);
        }

        /**
         * Filterable wrapper for HAVING clause.
         */
        public record HavingFilterable(SubQueryBuilder parent) implements Filterable {

            @Override
            public void addFilter(FilterOption filter) {
                parent.addHavingFilter(filter);
            }
        }
    }

    /**
     * Represents a reference to a field in the outer query.
     * Used for correlated subqueries.
     */
    public record OuterFieldReference(String fieldName) {}

    /**
     * Field definition that wraps a subquery (for SELECT clause).
     */
    public record SubQueryFieldDefinition(
        SubQuery subQuery,
        String alias
    ) implements FieldDefinition {
        
        @Override
        public String getAlias() {
            return alias;
        }
        
        @Override
        public String getFieldName() {
            return alias; // For subqueries in SELECT, alias is the field name
        }
    }
}