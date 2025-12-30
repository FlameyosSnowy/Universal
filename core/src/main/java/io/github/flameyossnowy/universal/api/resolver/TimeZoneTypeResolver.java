package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.util.TimeZone;

public class TimeZoneTypeResolver implements TypeResolver<TimeZone> {
    @Override public Class<TimeZone> getType() { return TimeZone.class; }
    @Override public Class<?> getDatabaseType() { return String.class; }

    @Override
    public TimeZone resolve(DatabaseResult result, String columnName) {
        String s = result.get(columnName, String.class);
        return s != null ? TimeZone.getTimeZone(s) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, TimeZone value) {
        parameters.set(index, value != null ? value.getID() : null, String.class);
    }
}
