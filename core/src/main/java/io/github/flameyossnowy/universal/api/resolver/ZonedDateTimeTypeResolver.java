package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;

public final class ZonedDateTimeTypeResolver implements TypeResolver<ZonedDateTime> {
    @Override
    public Class<ZonedDateTime> getType() {
        return ZonedDateTime.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable ZonedDateTime resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? ZonedDateTime.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, ZonedDateTime value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
