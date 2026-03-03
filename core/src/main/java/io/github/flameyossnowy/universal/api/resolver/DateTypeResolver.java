package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

import java.sql.Date;

public final class DateTypeResolver implements TypeResolver<Date> {
    @Override
    public Class<Date> getType() {
        return Date.class;
    }

    @Override
    public Class<Date> getDatabaseType() {
        return Date.class;
    }

    @Override
    public Date resolve(DatabaseResult result, String columnName) {
        return result.get(columnName, Date.class);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Date value) {
        parameters.set(index, value, Date.class);
    }
}
