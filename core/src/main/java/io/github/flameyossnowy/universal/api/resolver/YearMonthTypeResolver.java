package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.YearMonth;

public final class YearMonthTypeResolver implements TypeResolver<YearMonth> {
    @Override
    public Class<YearMonth> getType() {
        return YearMonth.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable YearMonth resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? YearMonth.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, YearMonth value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
