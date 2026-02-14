package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public record UpdateQuery(Map<String, Object> updates, List<FilterOption> filters) implements Query {
    public static class UpdateQueryBuilder implements Filterable {
        private final Map<String, Object> updates = new HashMap<>(3);
        private final List<FilterOption> conditions = new ArrayList<>(3);

        public UpdateQueryBuilder set(String field, Object value) {
            updates.put(field, value);
            return this;
        }

        /**
         * Adds a condition to the query based on the specified key, operator, and value.
         *
         * @param field the field to apply the condition on
         * @param operator the comparison operator (e.g., '=', '<', '>', etc.)
         * @param value the value to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        @Deprecated
        @ApiStatus.ScheduledForRemoval(inVersion = "7.2.0")
        public UpdateQueryBuilder where(String field, String operator, Object value) {
            conditions.add(new SelectOption(field, operator, value));
            return this;
        }

        /**
         * Adds a condition to the query based on the specified key and value.
         *
         * @param field the field to apply the condition on
         * @param value the value to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        public UpdateQueryBuilder where(String field, Object value) {
            conditions.add(new SelectOption(field, "=", value));
            return this;
        }

        /**
         * Adds a condition to the query based on the specified key and value.
         *
         * @param option the field to apply the condition on
         * @return the query field
         */
        public QueryField<UpdateQueryBuilder> where(String option) {
            return new QueryField<>(this, option);
        }

        /**
         * Adds a condition to the query based on the specified key and value.
         *
         * @param option the field to apply the condition on
         * @return the query field
         */
        public QueryField<UpdateQueryBuilder> whereJson(String option, String jsonPath) {
            return new QueryField<>(this, option, jsonPath);
        }

        /**
         * Adds a condition to the query based on the specified key and list of values, using the
         * {@code IN} operator.
         *
         * @param key the field to apply the condition on
         * @param values the list of values to compare the field against
         * @return the updated SelectQueryBuilder instance
         */
        @Deprecated
        @ApiStatus.ScheduledForRemoval(inVersion = "7.2.0")
        public UpdateQueryBuilder whereIn(String key, Collection<?> values) {
            conditions.add(new SelectOption(key, "IN", values));
            return this;
        }

        public UpdateQueryBuilder whereJson(
            String field,
            String jsonPath,
            String operator,
            Object value
        ) {
            conditions.add(new JsonSelectOption(field, jsonPath, operator, value));
            return this;
        }

        public UpdateQuery build() {
            return new UpdateQuery(updates, conditions);
        }

        public void addFilter(FilterOption filter) {
            conditions.add(filter);
        }
    }
}