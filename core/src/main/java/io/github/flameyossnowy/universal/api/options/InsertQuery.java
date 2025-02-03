package io.github.flameyossnowy.universal.api.options;

import java.util.HashSet;
import java.util.Set;

public record InsertQuery(Set<Object> data) {
    public static class InsertQueryBuilder {
        private final Set<Object> data = new HashSet<>();

        public InsertQueryBuilder value(Object value) {
            data.add(value);
            return this;
        }

        public InsertQuery build() {
            return new InsertQuery(data);
        }
    }
}