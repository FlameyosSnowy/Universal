package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class LocaleTypeResolver implements TypeResolver<Locale> {
    @Override
    public Class<Locale> getType() {
        return Locale.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Locale resolve(DatabaseResult result, String columnName) {
        String localeString = result.get(columnName, String.class);
        return localeString != null ? Locale.forLanguageTag(localeString) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Locale value) {
        parameters.set(index, value != null ? value.toLanguageTag() : null, String.class);
    }
}
