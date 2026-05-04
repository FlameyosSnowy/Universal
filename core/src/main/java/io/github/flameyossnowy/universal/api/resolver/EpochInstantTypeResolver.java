package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public final class EpochInstantTypeResolver implements TypeResolver<Instant> {
    @Override
    public Class<Instant> getType() {
        return Instant.class;
    }

    @Override
    public Class<Long> getDatabaseType() {
        return Long.class;
    }

    @Override
    public @Nullable Instant resolve(DatabaseResult result, String columnName) {
        Long raw = result.get(columnName, Long.class);
        return Instant.ofEpochMilli(raw);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Instant value) {
        parameters.set(index, value != null ? value.toEpochMilli() : null, Long.class);
    }

    @Override
    public SqlEncoding getEncoding() {
        return SqlEncoding.BINARY;
    }
}
