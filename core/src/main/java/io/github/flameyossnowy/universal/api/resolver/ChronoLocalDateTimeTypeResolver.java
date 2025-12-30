package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDateTime;

public class ChronoLocalDateTimeTypeResolver implements TypeResolver<ChronoLocalDateTime<?>> {
    @Override
    public Class<ChronoLocalDateTime<?>> getType() { return (Class<ChronoLocalDateTime<?>>)(Class<?>) ChronoLocalDateTime.class; }

    @Override
    public Class<Timestamp> getDatabaseType() { return Timestamp.class; }

    @Override
    public @Nullable ChronoLocalDateTime<?> resolve(DatabaseResult result, String columnName) {
        Timestamp ts = result.get(columnName, Timestamp.class);
        return ts != null ? LocalDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, ChronoLocalDateTime<?> value) {
        if (value == null) {
            parameters.set(index, null, Timestamp.class);
        } else {
            LocalDateTime ldt = LocalDateTime.from(value);
            parameters.set(index, Timestamp.valueOf(ldt), Timestamp.class);
        }
    }
}