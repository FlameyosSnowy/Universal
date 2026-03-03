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
        Object raw = result.get(columnName, Object.class);
        switch (raw) {
            case null -> {
                return null;
            }
            case Long l -> {
                return Instant.ofEpochMilli(l);
            }
            case Number n -> {
                return Instant.ofEpochMilli(n.longValue());
            }
            case String s -> {
                try {
                    return Instant.ofEpochMilli(Long.parseLong(s));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Cannot parse epoch millis from string: " + s, e);
                }
            }
            default -> {
            }
        }

        throw new IllegalArgumentException(
            "Unsupported epoch instant type for column '" + columnName + "': " + raw.getClass().getName()
        );
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
