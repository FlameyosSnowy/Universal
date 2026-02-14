package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record DeleteQuery(List<FilterOption> filters, boolean cache) implements Query {
    public static class DeleteQueryBuilder {
        private final List<FilterOption> filters = new ArrayList<>(3);
        private boolean cache = true;

        /**
         * Adds a condition to the query based on the specified key, operator, and value.
         *
         * @param option the field to apply the condition on
         * @param operator the comparison operator (e.g., '=', '<', '>', etc.)
         * @param value the value to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        public DeleteQueryBuilder where(String option, String operator, Object value) {
            filters.add(new SelectOption(option, operator, value));
            return this;
        }

        public DeleteQueryBuilder cache(boolean cache) {
            this.cache = cache;
            return this;
        }

        /**
         * Adds a condition to the query based on the specified key and value.
         *
         * @param option the field to apply the condition on
         * @param value the value to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        public DeleteQueryBuilder where(String option, Object value) {
            filters.add(new SelectOption(option, "=", value));
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
        public DeleteQueryBuilder whereIn(String key, Collection<?> values) {
            filters.add(new SelectOption(key, "IN", values));
            return this;
        }

        public DeleteQueryBuilder whereJson(
            String field,
            String jsonPath,
            String operator,
            Object value
        ) {
            filters.add(new JsonSelectOption(field, jsonPath, operator, value));
            return this;
        }

        public DeleteQuery build() {
            return new DeleteQuery(filters, cache);
        }
    }
}