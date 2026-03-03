package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.sql.Date;
import java.time.LocalDate;

public final class LocalDateTypeResolver implements TypeResolver<LocalDate> {
    @Override
    public Class<LocalDate> getType() {
        return LocalDate.class;
    }

    @Override
    public Class<Date> getDatabaseType() {
        return Date.class;
    }

    @Override
    public @Nullable LocalDate resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case LocalDate ld -> ld;
            case Date d -> d.toLocalDate();
            case java.util.Date d -> new Date(d.getTime()).toLocalDate();
            case String s -> LocalDate.parse(s);
            default -> throw new IllegalArgumentException(
                "Unsupported LocalDate type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, LocalDate value) {
        parameters.set(index, value != null ? Date.valueOf(value) : null, Date.class);
    }
}
