package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.*;

/**
 * Immutable query description used by repository adapters.
 *
 * <p>This query model is intentionally backend-agnostic. It represents
 * filtering, sorting, limiting, and eager-loading intent without assuming
 * SQL, MongoDB, or any specific storage engine.</p>
 *
 * <p>Instances should be constructed exclusively through
 * {@link SelectQueryBuilder}.</p>
 */
public record SelectQuery(
    List<String> columns,
    List<FilterOption> filters,
    List<SortOption> sortOptions,
    int limit,
    String joinTable
) implements Query {

    /**
     * Fluent builder for {@link SelectQuery}.
     *
     * <p>The modern API is field-scoped and operator-driven:
     * {@code where("age").gte(18)} instead of constructing filter objects
     * manually.</p>
     */
    public static class SelectQueryBuilder implements Filterable {

        private final List<FilterOption> filters = new ArrayList<>();
        private final List<SortOption> sortOptions = new ArrayList<>();
        private final List<String> columns;

        private int limit = -1;
        private String joinTable;

        public SelectQueryBuilder(String... columns) {
            this.columns = List.of(columns);
        }

        /**
         * Begins a filter clause for the given field.
         *
         * <pre>{@code
         * Query.select()
         *   .where("age").gte(18)
         *   .where("name").like("A%")
         *   .build();
         * }</pre>
         */
        public QueryField<SelectQueryBuilder> where(String field) {
            return new QueryField<>(this, field);
        }

        /**
         * Begins a filter clause for the given field in JSON
         */
        public QueryField<SelectQueryBuilder> whereJson(String field, String jsonPath) {
            return new QueryField<>(this, field, jsonPath);
        }

        /* =========================
           Deprecated legacy methods
           ========================= */

        /**
         * @deprecated Use {@link #where(String)} followed by a comparison operator.
         *
         * <p><b>Preferred:</b>
         * <pre>{@code
         * Query.select()
         *   .where("age").gte(18)
         *   .build();
         * }</pre>
         *
         * <p>This method allows bypassing validation and constructing
         * backend-incompatible filters.</p>
         */
        @ApiStatus.ScheduledForRemoval(inVersion = "7.2.0")
        @Deprecated(forRemoval = true, since = "7.0.0")
        public SelectQueryBuilder where(FilterOption filter) {
            filters.add(filter);
            return this;
        }

        public SelectQueryBuilder where(List<FilterOption> filters) {
            this.filters.addAll(filters);
            return this;
        }

        /**
         * @deprecated Use {@link #where(String)} with comparison methods.
         *
         * <p><b>Preferred:</b>
         * <pre>{@code
         * Query.select()
         *   .where("score").gt(100)
         *   .build();
         * }</pre>
         */
        @ApiStatus.ScheduledForRemoval(inVersion = "7.2.0")
        @Deprecated(forRemoval = true, since = "7.0.0")
        public SelectQueryBuilder where(String key, String operator, Object value) {
            filters.add(new SelectOption(key, operator, value));
            return this;
        }

        /**
         * @deprecated Use {@link #where(String)} followed by {@code .eq(value)}.
         *
         * <p><b>Preferred:</b>
         * <pre>{@code
         * Query.select()
         *   .where("id").eq(42)
         *   .build();
         * }</pre>
         */
        @ApiStatus.ScheduledForRemoval(inVersion = "7.2.0")
        @Deprecated(forRemoval = true, since = "7.0.0")
        public SelectQueryBuilder where(String key, Object value) {
            filters.add(new SelectOption(key, "=", value));
            return this;
        }

        /**
         * @deprecated Use {@link QueryField#in(Collection)} instead.
         *
         * <p><b>Preferred:</b>
         * <pre>{@code
         * Query.select()
         *   .where("id").in(List.of(1, 2, 3))
         *   .build();
         * }</pre>
         */
        @ApiStatus.ScheduledForRemoval(inVersion = "7.2.0")
        @Deprecated(forRemoval = true, since = "7.0.0")
        public SelectQueryBuilder whereIn(String key, Collection<?> values) {
            filters.add(new SelectOption(key, "IN", values));
            return this;
        }

        /* =========================
           Modern builder API
           ========================= */

        public SelectQueryBuilder orderBy(String field, SortOrder direction) {
            sortOptions.add(new SortOption(field, direction));
            return this;
        }

        public SelectQueryBuilder orderBy(SortOption option) {
            sortOptions.add(option);
            return this;
        }

        public SelectQueryBuilder orderBy(List<SortOption> options) {
            sortOptions.addAll(options);
            return this;
        }

        public SelectQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SelectQuery build() {
            return new SelectQuery(
                new ArrayList<>(columns),
                new ArrayList<>(filters),
                new ArrayList<>(sortOptions),
                limit,
                joinTable
            );
        }

        public void addFilter(FilterOption filter) {
            filters.add(filter);
        }
    }
}
