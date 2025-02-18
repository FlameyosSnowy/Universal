package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.List;

public record SelectQuery(List<String> columns, List<SelectOption> filters, List<SortOption> sortOptions, List<JoinOption> joins, int limit) implements Query {
    public static class SelectQueryBuilder {
        private final List<String> columns;
        private final List<SelectOption> filters = new ArrayList<>();
        private final List<SortOption> sortOptions = new ArrayList<>();
        private final List<JoinOption> joins = new ArrayList<>();
        private int limit = -1;

        public SelectQueryBuilder(String... columns) {
            this.columns = List.of(columns);
        }

        public SelectQueryBuilder where(String key, String operator, Object value) {
            filters.add(new SelectOption(key, operator, value));
            return this;
        }

        public SelectQueryBuilder where(String key, Object value) {
            filters.add(new SelectOption(key, "=", value));
            return this;
        }

        public SelectQueryBuilder orderBy(String field, SortOrder direction) {
            sortOptions.add(new SortOption(field, direction));
            return this;
        }

        public SelectQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SelectQueryBuilder join(String joinType, String targetTable, String onCondition) {
            joins.add(new JoinOption(joinType, targetTable, onCondition));
            return this;
        }

        public SelectQuery build() {
            return new SelectQuery(columns, filters, sortOptions, joins, limit);
        }
    }
}
