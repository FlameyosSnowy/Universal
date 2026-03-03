package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public final class LocalDateTimeTypeResolver implements TypeResolver<LocalDateTime> {
    @Override
    public Class<LocalDateTime> getType() {
        return LocalDateTime.class;
    }

    @Override
    public Class<Timestamp> getDatabaseType() {
        return Timestamp.class;
    }

    @Override
    public @Nullable LocalDateTime resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case LocalDateTime ldt -> ldt;
            case Timestamp ts -> ts.toLocalDateTime();
            case java.util.Date d -> new Timestamp(d.getTime()).toLocalDateTime();
            case String s -> LocalDateTime.parse(s);
            default -> throw new IllegalArgumentException(
                "Unsupported LocalDateTime type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, LocalDateTime value) {
        parameters.set(index, value != null ? Timestamp.valueOf(value) : null, Timestamp.class);
    }
}
