package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.time.Year;

public class YearTypeResolver implements TypeResolver<Year> {
    @Override public Class<Year> getType() { return Year.class; }
    @Override public Class<?> getDatabaseType() { return Integer.class; }

    @Override
    public Year resolve(DatabaseResult result, String columnName) {
        Integer value = result.get(columnName, Integer.class);
        return value != null ? Year.of(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Year value) {
        parameters.set(index, value != null ? value.getValue() : null, Integer.class);
    }
}
