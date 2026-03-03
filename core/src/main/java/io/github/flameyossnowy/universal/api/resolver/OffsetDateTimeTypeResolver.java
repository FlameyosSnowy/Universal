package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

public final class OffsetDateTimeTypeResolver implements TypeResolver<OffsetDateTime> {
    @Override
    public Class<OffsetDateTime> getType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable OffsetDateTime resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? OffsetDateTime.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, OffsetDateTime value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
