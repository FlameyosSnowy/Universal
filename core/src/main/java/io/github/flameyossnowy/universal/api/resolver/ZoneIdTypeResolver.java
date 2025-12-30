package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.time.ZoneId;

public class ZoneIdTypeResolver implements TypeResolver<ZoneId> {
    @Override public Class<ZoneId> getType() { return ZoneId.class; }
    @Override public Class<?> getDatabaseType() { return String.class; }

    @Override
    public ZoneId resolve(DatabaseResult result, String columnName) {
        String s = result.get(columnName, String.class);
        return s != null ? ZoneId.of(s) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, ZoneId value) {
        parameters.set(index, value != null ? value.getId() : null, String.class);
    }
}