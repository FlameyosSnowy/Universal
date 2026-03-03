package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UriTypeResolver implements TypeResolver<URI> {
    private static final Map<String, URI> CACHE = new ConcurrentHashMap<>(3);

    @Override
    public Class<URI> getType() {
        return URI.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable URI resolve(DatabaseResult result, String columnName) {
        String uriString = result.get(columnName, String.class);
        if (uriString == null) return null;
        return CACHE.computeIfAbsent(uriString, s -> {
            try {
                return new URI(s);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Invalid URI in database: " + s, e);
            }
        });
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, URI value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
