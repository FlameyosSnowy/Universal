package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PathTypeResolver implements TypeResolver<Path> {
    private static final Map<String, Path> CACHE = new ConcurrentHashMap<>(3);

    @Override
    public Class<Path> getType() {
        return Path.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable Path resolve(DatabaseResult result, String columnName) {
        String pathString = result.get(columnName, String.class);
        if (pathString == null) return null;
        return CACHE.computeIfAbsent(pathString, Paths::get);
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, Path value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
