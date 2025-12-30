package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetTime;

public class OffsetTimeTypeResolver implements TypeResolver<OffsetTime> {
    @Override
    public Class<OffsetTime> getType() { return OffsetTime.class; }

    @Override
    public Class<String> getDatabaseType() { return String.class; }

    @Override
    public @Nullable OffsetTime resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? OffsetTime.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, OffsetTime value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}