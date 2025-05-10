package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.List;

public record SelectQuery(List<String> columns, List<SelectOption> filters, List<SortOption> sortOptions, int limit) implements Query {
    public static class SelectQueryBuilder {
        private final List<String> columns;
        private final List<SelectOption> filters = new ArrayList<>(2);
        private final List<SortOption> sortOptions = new ArrayList<>(1);
        private int limit = -1;

        public SelectQueryBuilder(String... columns) {
            this.columns = List.of(columns);
        }

        /**
        * Adds a condition to the query based on the specified key, operator, and value.
        *
        * @param key the field to apply the condition on
        * @param operator the comparison operator (e.g., '=', '<', '>', etc.)
        * @param value the value to compare the field against
        * @return the updated SelectQueryBuilder instance
        */
        public SelectQueryBuilder where(String key, String operator, Object value) {
            filters.add(new SelectOption(key, operator, value));
            return this;
        }

        /**
         * Adds a condition to the query based on the specified key and value, using the
         * default equality operator.
         *
         * @param key the field to apply the condition on
         * @param value the value to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        public SelectQueryBuilder where(String key, Object value) {
            filters.add(new SelectOption(key, "=", value));
            return this;
        }

        /**
         * Adds a condition to the query based on the specified key and list of values, using the
         * {@code IN} operator.
         *
         * @param key the field to apply the condition on
         * @param values the list of values to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        public SelectQueryBuilder whereIn(String key, List<?> values) {
            filters.add(new SelectOption(key, "IN", values));
            return this;
        }

        /**
         * Specifies the field and direction to order the query results by.
         *
         * @param field the field to order the results by
         * @param direction the direction to sort the results, either ascending or descending
         * @return the updated SelectQueryBuilder instance
         */
        public SelectQueryBuilder orderBy(String field, SortOrder direction) {
            sortOptions.add(new SortOption(field, direction));
            return this;
        }

        /**
         * Limits the number of records to be returned from the query.
         *
         * @param limit the maximum number of records to return. If set to a negative value, the limit
         *              will be disabled.
         * @return the updated SelectQueryBuilder instance
         */
        public SelectQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Builds a new SelectQuery instance from the current configuration.
         *
         * @return a new SelectQuery instance
         */
        public SelectQuery build() {
            return new SelectQuery(columns, filters, sortOptions, limit);
        }
    }
}
