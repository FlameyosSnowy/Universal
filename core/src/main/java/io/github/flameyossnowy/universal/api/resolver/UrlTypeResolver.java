package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UrlTypeResolver implements TypeResolver<URL> {
    private static final Map<String, URL> CACHE = new ConcurrentHashMap<>(3);

    @Override
    public Class<URL> getType() {
        return URL.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable URL resolve(DatabaseResult result, String columnName) {
        String urlString = result.get(columnName, String.class);
        if (urlString == null) return null;
        return CACHE.computeIfAbsent(urlString, s -> {
            try {
                return new URI(s).toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Invalid URL in database: " + s, e);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, URL value) {
        parameters.set(index, value != null ? value.toString() : null, String.class);
    }
}
