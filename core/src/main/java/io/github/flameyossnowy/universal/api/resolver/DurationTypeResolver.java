package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public final class DurationTypeResolver implements TypeResolver<Duration> {
    @Override
    public Class<Duration> getType() {
        return Duration.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Duration resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case Duration d -> d;
            case String s -> Duration.parse(s);
            case Number n ->
                // Assume milliseconds
                Duration.ofMillis(n.longValue());
            default -> throw new IllegalArgumentException(
                "Unsupported Duration type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Duration value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
