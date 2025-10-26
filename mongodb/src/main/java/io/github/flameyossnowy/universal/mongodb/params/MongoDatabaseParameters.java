package io.github.flameyossnowy.universal.mongodb.params;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * MongoDB implementation of DatabaseParameters that builds a BSON Document.
 * Supports mapping between repository fields and query parameters.
 */
public class MongoDatabaseParameters implements DatabaseParameters {
    private final Document document = new Document();
    private TypeResolverRegistry typeResolverRegistry;

    /**
     * Creates a new instance with default settings.
     */
    public MongoDatabaseParameters(TypeResolverRegistry typeResolverRegistry) {
        this.typeResolverRegistry = typeResolverRegistry;
    }

    @Override
    public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        document.put(name, value);
    }

    @Override
    public void setNull(@NotNull String name, @NotNull Class<?> type) {
        set(name, null, type);
    }

    @Override
    public int size() {
        return document.size();
    }

    @Override
    public <T> @Nullable T get(int index, @NotNull Class<T> type) {
        throw new UnsupportedOperationException("Cannot get positional parameters in MongoDB");
    }

    @Override
    public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) {
        return document.get(name, type);
    }

    @Override
    public boolean contains(@NotNull String name) {
        return document.containsKey(name);
    }

    /**
     * Get the underlying BSON document.
     * @return the BSON document
     */
    public @NotNull Document toDocument() {
        return new Document(document);
    }
}
