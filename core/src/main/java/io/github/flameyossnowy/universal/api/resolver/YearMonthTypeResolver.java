package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.time.YearMonth;

public class YearMonthTypeResolver implements TypeResolver<YearMonth> {
    @Override public Class<YearMonth> getType() { return YearMonth.class; }
    @Override public Class<?> getDatabaseType() { return String.class; } // "yyyy-MM"

    @Override
    public YearMonth resolve(DatabaseResult result, String columnName) {
        String s = result.get(columnName, String.class);
        return s != null ? YearMonth.parse(s) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, YearMonth value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
