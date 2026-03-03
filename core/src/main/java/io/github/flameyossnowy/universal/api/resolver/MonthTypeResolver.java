package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.time.Month;

public final class MonthTypeResolver implements TypeResolver<Month> {
    @Override
    public Class<Month> getType() {
        return Month.class;
    }

    @Override
    public Class<Integer> getDatabaseType() {
        return Integer.class;
    }

    @Override
    public @Nullable Month resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        switch (raw) {
            case null -> {
                return null;
            }
            case Month m -> {
                return m;
            }
            case Number n -> {
                return Month.of(n.intValue());
            }
            case String s -> {
                try {
                    return Month.of(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    return Month.valueOf(s.toUpperCase());
                }
            }
            default -> {
            }
        }

        throw new IllegalArgumentException(
            "Unsupported Month type for column '" + columnName + "': " + raw.getClass().getName()
        );
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Month value) {
        parameters.set(index, value != null ? value.getValue() : null, Integer.class);
    }
}
