package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.List;

public record DeleteQuery(List<SelectOption> filters) {
    public static class DeleteQueryBuilder {
        private final List<SelectOption> filters = new ArrayList<>();

        public DeleteQueryBuilder where(String option, String operator, Object value) {
            filters.add(new SelectOption(option, operator, value));
            return this;
        }

        public DeleteQuery build() {
            return new DeleteQuery(filters);
        }
    }
}