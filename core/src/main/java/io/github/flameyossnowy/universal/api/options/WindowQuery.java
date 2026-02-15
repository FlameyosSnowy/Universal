package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Query type for window functions.
 *
 * <p>Examples:
 * <pre>{@code
 * // Row number partitioned by department
 * Query.window()
 *     .select(
 *         field("name"),
 *         field("salary"),
 *         field("id")
 *             .rowNumber()
 *             .partitionBy("departmentId")
 *             .orderBy("salary", DESC)
 *             .as("rank")
 *     )
 *     .from(Employee.class)
 *     .build();
 * 
 * // Cumulative count
 * Query.window()
 *     .select(
 *         field("name"),
 *         field("hireDate"),
 *         field("id")
 *             .cumulativeCount()
 *             .partitionBy("departmentId")
 *             .orderBy("hireDate", ASC)
 *             .as("hire_sequence")
 *     )
 *     .build();
 * 
 * // Running total
 * Query.window()
 *     .select(
 *         field("date"),
 *         field("amount"),
 *         field("amount")
 *             .cumulativeSum()
 *             .orderBy("date", ASC)
 *             .rowsBetweenUnboundedPrecedingAndCurrentRow()
 *             .as("running_total")
 *     )
 *     .build();
 * }</pre>
 */
public record WindowQuery(
    @NotNull List<FieldDefinition> selectFields,
    Class<?> fromTable,
    @NotNull List<FilterOption> whereFilters,
    @NotNull List<SortOption> orderBy,
    int limit
) implements Query {

    public static class WindowQueryBuilder implements Filterable {
        private final List<FieldDefinition> selectFields = new ArrayList<>();
        private final List<FilterOption> whereFilters = new ArrayList<>();
        private final List<SortOption> orderBy = new ArrayList<>();
        private Class<?> fromTable;
        private int limit = -1;

        /**
         * Add fields to SELECT clause (including window functions).
         * 
         * <pre>{@code
         * .select(
         *     field("name"),
         *     field("salary").rowNumber()
         *         .partitionBy("departmentId")
         *         .orderBy("salary", DESC)
         *         .as("rank")
         * )
         * }</pre>
         */
        public WindowQueryBuilder select(FieldDefinition... fields) {
            selectFields.addAll(List.of(fields));
            return this;
        }

        /**
         * Select a simple field.
         */
        public WindowQueryBuilder select(String field) {
            selectFields.add(new SimpleFieldDefinition(field, null));
            return this;
        }

        /**
         * Specify the source table.
         */
        public WindowQueryBuilder from(Class<?> table) {
            this.fromTable = table;
            return this;
        }

        /**
         * Add WHERE filters.
         */
        public QueryField<WindowQueryBuilder> where(String field) {
            return new QueryField<>(this, field);
        }

        /**
         * Add ORDER BY clause (applies to entire result set, not window).
         */
        public WindowQueryBuilder orderBy(String field, SortOrder direction) {
            orderBy.add(new SortOption(field, direction));
            return this;
        }

        /**
         * Limit results.
         */
        public WindowQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        @Override
        public void addFilter(FilterOption filter) {
            whereFilters.add(filter);
        }

        public WindowQuery build() {
            return new WindowQuery(
                new ArrayList<>(selectFields),
                fromTable,
                new ArrayList<>(whereFilters),
                new ArrayList<>(orderBy),
                limit
            );
        }
    }
}