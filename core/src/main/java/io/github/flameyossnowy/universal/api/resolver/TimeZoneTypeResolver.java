package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.util.TimeZone;

public final class TimeZoneTypeResolver implements TypeResolver<TimeZone> {
    @Override
    public Class<TimeZone> getType() {
        return TimeZone.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable TimeZone resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? TimeZone.getTimeZone(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, TimeZone value) {
        parameters.set(index, value != null ? value.getID() : null, String.class);
    }
}
