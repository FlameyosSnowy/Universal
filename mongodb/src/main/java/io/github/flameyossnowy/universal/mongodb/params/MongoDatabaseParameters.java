package io.github.flameyossnowy.universal.mongodb.params;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MongoDB implementation of DatabaseParameters that builds a BSON Document.
 * Supports mapping between repository fields and query parameters.
 */
public class MongoDatabaseParameters implements DatabaseParameters {
    private final Document document = new Document();
    private RepositoryInformation repositoryInfo;
    private TypeResolverRegistry typeResolverRegistry;

    /**
     * Creates a new instance with default settings.
     */
    public MongoDatabaseParameters(TypeResolverRegistry typeResolverRegistry) {
        this.typeResolverRegistry = typeResolverRegistry;
    }

    /**
     * Creates a new instance with the specified repository information.
     *
     * @param repositoryInfo the repository information
     */
    public MongoDatabaseParameters(RepositoryInformation repositoryInfo) {
        this.repositoryInfo = repositoryInfo;
    }

    /**
     * Creates a new instance with the specified repository information and type resolver registry.
     *
     * @param repositoryInfo the repository information
     * @param typeResolverRegistry the type resolver registry to use
     */
    public MongoDatabaseParameters(RepositoryInformation repositoryInfo, TypeResolverRegistry typeResolverRegistry) {
        this.repositoryInfo = repositoryInfo;
        this.typeResolverRegistry = typeResolverRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        document.put(name, value);
    }

    @Override
    public void setNull(int index, @NotNull Class<?> type) {
        set(index, null, type);
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
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(int index, @NotNull Class<T> type) {
        throw new UnsupportedOperationException("Cannot get positional parameters in MongoDB");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) {
        return document.get(name, type);
    }

    @Override
    public boolean contains(@NotNull String name) {
        return document.containsKey(name);
    }

    /**
     * Gets the repository information associated with these parameters.
     * @return the repository information, or null if not set
     */
    public @Nullable RepositoryInformation getRepositoryInfo() {
        return repositoryInfo;
    }

    /**
     * Sets the repository information for these parameters.
     * @param repositoryInfo the repository information
     */
    public void setRepositoryInfo(@Nullable RepositoryInformation repositoryInfo) {
        this.repositoryInfo = repositoryInfo;
    }

    /**
     * Gets the type resolver registry being used.
     * @return the type resolver registry
     */
    public @NotNull TypeResolverRegistry getTypeResolverRegistry() {
        return typeResolverRegistry;
    }

    /**
     * Sets the type resolver registry to use.
     * @param typeResolverRegistry the type resolver registry
     */
    public void setTypeResolverRegistry(@NotNull TypeResolverRegistry typeResolverRegistry) {
        this.typeResolverRegistry = typeResolverRegistry;
    }

    /**
     * Get the underlying BSON document.
     * @return the BSON document
     */
    public @NotNull Document toDocument() {
        return new Document(document);
    }

    /**
     * Convert to a BSON filter for queries.
     * @return BSON filter
     */
    public @NotNull Bson toBson() {
        return new Document(document);
    }
}
