package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public final class InstantTypeResolver implements TypeResolver<Instant> {
    @Override
    public Class<Instant> getType() {
        return Instant.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Instant resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? Instant.parse(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Instant value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
