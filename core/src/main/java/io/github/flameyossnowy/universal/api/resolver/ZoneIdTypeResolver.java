package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;

public final class ZoneIdTypeResolver implements TypeResolver<ZoneId> {
    @Override
    public Class<ZoneId> getType() {
        return ZoneId.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable ZoneId resolve(DatabaseResult result, String columnName) {
        String value = result.get(columnName, String.class);
        return value != null ? ZoneId.of(value) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, ZoneId value) {
        parameters.set(index, value != null ? value.getId() : null, String.class);
    }
}
