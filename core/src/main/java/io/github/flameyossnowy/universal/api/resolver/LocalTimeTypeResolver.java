package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.sql.Time;
import java.time.LocalTime;

public final class LocalTimeTypeResolver implements TypeResolver<LocalTime> {
    @Override
    public Class<LocalTime> getType() {
        return LocalTime.class;
    }

    @Override
    public Class<Time> getDatabaseType() {
        return Time.class;
    }

    @Override
    public @Nullable LocalTime resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case LocalTime lt -> lt;
            case Time t -> t.toLocalTime();
            case String s -> LocalTime.parse(s);
            default -> throw new IllegalArgumentException(
                "Unsupported LocalTime type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, LocalTime value) {
        parameters.set(index, value != null ? Time.valueOf(value) : null, Time.class);
    }
}
