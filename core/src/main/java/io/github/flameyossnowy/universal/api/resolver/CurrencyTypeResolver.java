package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.util.Currency;

public final class CurrencyTypeResolver implements TypeResolver<Currency> {
    @Override
    public Class<Currency> getType() {
        return Currency.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Currency resolve(DatabaseResult result, String columnName) {
        String currencyCode = result.get(columnName, String.class);
        return currencyCode != null ? Currency.getInstance(currencyCode) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Currency value) {
        parameters.set(index, value != null ? value.getCurrencyCode() : null, String.class);
    }
}
