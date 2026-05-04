package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.sql.Timestamp;

public final class TimestampTypeResolver implements TypeResolver<Timestamp> {
    @Override
    public Class<Timestamp> getType() {
        return Timestamp.class;
    }

    @Override
    public Class<Timestamp> getDatabaseType() {
        return Timestamp.class;
    }

    @Override
    public Timestamp resolve(DatabaseResult result, String columnName) {
        return result.get(columnName, Timestamp.class);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Timestamp value) {
        parameters.setRaw(index, value, Timestamp.class);
    }
}
