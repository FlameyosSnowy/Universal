package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.Period;

public final class PeriodTypeResolver implements TypeResolver<Period> {
    @Override
    public Class<Period> getType() {
        return Period.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Period resolve(DatabaseResult result, String columnName) {
        String periodString = result.get(columnName, String.class);
        return periodString != null ? Period.parse(periodString) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Period value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
