package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.Year;

public final class YearTypeResolver implements TypeResolver<Year> {
    @Override
    public Class<Year> getType() {
        return Year.class;
    }

    @Override
    public Class<Integer> getDatabaseType() {
        return Integer.class;
    }

    @Override
    public @Nullable Year resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case Year y -> y;
            case Number n -> Year.of(n.intValue());
            case String s -> Year.parse(s);
            default -> throw new IllegalArgumentException(
                "Unsupported Year type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Year value) {
        parameters.set(index, value != null ? value.getValue() : null, Integer.class);
    }
}
