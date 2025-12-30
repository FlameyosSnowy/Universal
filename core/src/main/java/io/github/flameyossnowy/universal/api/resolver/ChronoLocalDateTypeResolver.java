package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;

public class ChronoLocalDateTypeResolver implements TypeResolver<ChronoLocalDate> {
    @Override
    public Class<ChronoLocalDate> getType() { return ChronoLocalDate.class; }

    @Override
    public Class<String> getDatabaseType() { return String.class; }

    @Override
    public @Nullable ChronoLocalDate resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? LocalDate.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, ChronoLocalDate value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}