package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public final class BigIntegerTypeResolver implements TypeResolver<BigInteger> {
    @Override
    public Class<BigInteger> getType() {
        return BigInteger.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable BigInteger resolve(DatabaseResult result, String columnName) {
        Object raw = result.get(columnName, Object.class);
        return switch (raw) {
            case null -> null;
            case BigInteger bi -> bi;
            case String s -> new BigInteger(s);
            case Number n -> BigInteger.valueOf(n.longValue());
            default -> throw new IllegalArgumentException(
                "Unsupported BigInteger type for column '" + columnName + "': " + raw.getClass().getName()
            );
        };

    }

    @Override
    public void insert(DatabaseParameters parameters, String index, BigInteger value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
