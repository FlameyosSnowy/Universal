package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

public final class BigDecimalTypeResolver implements TypeResolver<BigDecimal> {
    @Override
    public Class<BigDecimal> getType() {
        return BigDecimal.class;
    }

    @Override
    public Class<BigDecimal> getDatabaseType() {
        return BigDecimal.class;
    }

    @Override
    public @Nullable BigDecimal resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case BigDecimal bd -> bd;
            case String s -> new BigDecimal(s);
            case Number n -> BigDecimal.valueOf(n.doubleValue());
            default -> throw new IllegalArgumentException(
                "Unsupported BigDecimal type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, BigDecimal value) {
        parameters.set(index, value, BigDecimal.class);
    }
}
