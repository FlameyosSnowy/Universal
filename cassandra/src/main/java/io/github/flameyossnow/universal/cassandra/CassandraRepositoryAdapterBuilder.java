package io.github.flameyossnow.universal.cassandra;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.DefaultSessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.LongFunction;

@SuppressWarnings("unused")
public class CassandraRepositoryAdapterBuilder<T, ID> {
    private CassandraCredentials credentials;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;
    private CacheWarmer<T, ID> cacheWarmer;

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new DefaultSessionCache<>();

    public CassandraRepositoryAdapterBuilder(Class<T> repository, Class<ID> idClass) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(idClass, "Repository cannot be null");
    }

    public CassandraRepositoryAdapterBuilder<T, ID> withCredentials(CassandraCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public CassandraRepositoryAdapterBuilder<T, ID> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    public CassandraRepositoryAdapterBuilder<T, ID> withOptimizations(Optimizations optimizations) {
        this.optimizations.add(optimizations);
        return this;
    }

    public CassandraRepositoryAdapterBuilder<T, ID> setSessionCacheSupplier(LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        return this;
    }

    /**
     * Sets the cache warmer for this repository adapter.
     * The cache warmer will be used to preload frequently accessed data into the cache.
     *
     * @param cacheWarmer The cache warmer to use
     * @return This builder for method chaining
     */
    public CassandraRepositoryAdapterBuilder<T, ID> withCacheWarmer(CacheWarmer<T, ID> cacheWarmer) {
        this.cacheWarmer = cacheWarmer;
        return this;
    }

    public CassandraRepositoryAdapterBuilder<T, ID> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    @SuppressWarnings("unchecked")
    public CassandraRepositoryAdapter<T, ID> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");
        RepositoryInformation information = Objects.requireNonNull(RepositoryMetadata.getMetadata(this.repository));

        Cacheable cacheable = information.getCacheable();
        GlobalCacheable globalCacheable = information.getGlobalCacheable();

        DefaultResultCache<String, T, ID> resultCache = cacheable != null ? new DefaultResultCache<>(cacheable.maxCacheSize(), cacheable.algorithm()) : null;

        if (globalCacheable != null) {
            try {
                return new CassandraRepositoryAdapter<>(
                        resultCache,
                        this.repository,
                        this.idClass,
                        (SessionCache<ID, T>) globalCacheable.sessionCache().getDeclaredConstructor().newInstance(),
                        sessionCacheSupplier,
                        credentials,
                        cacheWarmer
                );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        return new CassandraRepositoryAdapter<>(
                resultCache,
                repository,
                idClass,
                null,
                sessionCacheSupplier,
                credentials,
                cacheWarmer
        );
    }
}
