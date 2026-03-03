package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.MonthDay;

public final class MonthDayTypeResolver implements TypeResolver<MonthDay> {
    @Override
    public Class<MonthDay> getType() {
        return MonthDay.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable MonthDay resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? MonthDay.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, MonthDay value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
