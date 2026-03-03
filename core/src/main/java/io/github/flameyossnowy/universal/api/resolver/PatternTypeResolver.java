package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PatternTypeResolver implements TypeResolver<Pattern> {
    private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>(3);

    @Override
    public Class<Pattern> getType() {
        return Pattern.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Pattern resolve(DatabaseResult result, String columnName) {
        String patternString = result.get(columnName, String.class);
        if (patternString == null) return null;
        return CACHE.computeIfAbsent(patternString, Pattern::compile);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Pattern value) {
        parameters.set(index, value != null ? value.pattern() : null, String.class);
    }
}
