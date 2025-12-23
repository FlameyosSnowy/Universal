package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public record UpdateQuery(Map<String, Object> updates, List<SelectOption> filters) implements Query {
    public static class UpdateQueryBuilder {
        private final Map<String, Object> updates = new HashMap<>(3);
        private final List<SelectOption> conditions = new ArrayList<>(3);

        public UpdateQueryBuilder set(String field, Object value) {
            updates.put(field, value);
            return this;
        }

        public UpdateQueryBuilder where(String field, String operator, Object value) {
            conditions.add(new SelectOption(field, operator, value));
            return this;
        }

        public UpdateQuery build() {
            return new UpdateQuery(updates, conditions);
        }
    }
}