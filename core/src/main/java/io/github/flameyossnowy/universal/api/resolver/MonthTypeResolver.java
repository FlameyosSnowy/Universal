package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.time.Month;

public class MonthTypeResolver implements TypeResolver<Month> {
    @Override public Class<Month> getType() { return Month.class; }
    @Override public Class<?> getDatabaseType() { return Integer.class; } // store as 1-12

    @Override
    public Month resolve(DatabaseResult result, String columnName) {
        Integer value = result.get(columnName, Integer.class);
        return value != null ? Month.of(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Month value) {
        parameters.set(index, value != null ? value.getValue() : null, Integer.class);
    }
}
