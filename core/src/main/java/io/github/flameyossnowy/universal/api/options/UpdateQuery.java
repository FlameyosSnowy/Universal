package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateQuery {
    private final String table;
    private final Map<String, Object> updates;
    private final List<SelectOption> conditions;

    private UpdateQuery(final String table, final Map<String, Object> updates, final List<SelectOption> conditions) {
        this.table = table;
        this.updates = updates;
        this.conditions = conditions;
    }

    public String getTable() {
        return table;
    }

    public Map<String, Object> getUpdates() {
        return updates;
    }

    public List<SelectOption> getConditions() {
        return conditions;
    }

    public static class UpdateQueryBuilder {
        private String table;
        private final Map<String, Object> updates = new HashMap<>();
        private final List<SelectOption> conditions = new ArrayList<>();

        public UpdateQueryBuilder set(String field, Object value) {
            updates.put(field, value);
            return this;
        }

        public UpdateQueryBuilder where(String field, String operator, Object value) {
            conditions.add(new SelectOption(field, operator, value));
            return this;
        }

        public UpdateQuery build() {
            return new UpdateQuery(table, updates, conditions);
        }
    }
}
