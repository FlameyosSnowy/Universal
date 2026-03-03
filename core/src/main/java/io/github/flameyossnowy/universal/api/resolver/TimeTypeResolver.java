package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.sql.Time;

public final class TimeTypeResolver implements TypeResolver<Time> {
    @Override
    public Class<Time> getType() {
        return Time.class;
    }

    @Override
    public Class<Time> getDatabaseType() {
        return Time.class;
    }

    @Override
    public Time resolve(DatabaseResult result, String columnName) {
        return result.get(columnName, Time.class);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Time value) {
        parameters.set(index, value, Time.class);
    }
}
