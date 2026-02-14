package io.github.flameyossnowy.universal.postgresql;

import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.DefaultSessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.postgresql.connections.PostgreSQLSimpleConnectionProvider;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

@SuppressWarnings("unused")
public class PostgreSQLRepositoryAdapterBuilder<T, ID> {
    static {
        ModelsBootstrap.init();
    }

    private PostgreSQLCredentials credentials;
    private BiFunction<PostgreSQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;
    private CacheWarmer<T, ID> cacheWarmer;

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new DefaultSessionCache<>();

    public PostgreSQLRepositoryAdapterBuilder(Class<T> repository, Class<ID> idClass) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(idClass, "Repository cannot be null");
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> withConnectionProvider(BiFunction<PostgreSQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> withCacheWarmer(CacheWarmer<T, ID> cacheWarmer) {
        this.cacheWarmer = cacheWarmer;
        return this;
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> withCredentials(PostgreSQLCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> setSessionCacheSupplier(LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        return this;
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    @SuppressWarnings("unchecked")
    public PostgreSQLRepositoryAdapter<T, ID> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");
        RepositoryModel<T, ID> information = Objects.requireNonNull(GeneratedMetadata.getByEntityClass(this.repository));

        CacheConfig cacheable = information.getCacheConfig();

        boolean globalCacheable = information.isGlobalCacheable();

        boolean cacheEnabled = cacheable.isEnabled();
        int maxSize = 0;

        DefaultResultCache<String, T, ID> resultCache = null;

        if (cacheEnabled && cacheable.maxSize() > 0) {
            maxSize = cacheable.maxSize();
            resultCache = new DefaultResultCache<>(cacheable.maxSize(), cacheable.cacheAlgorithmType());
        }

        if (globalCacheable) {
            return new PostgreSQLRepositoryAdapter<>(
                this.connectionProvider != null
                    ? this.connectionProvider.apply(credentials, this.optimizations)
                    : new PostgreSQLSimpleConnectionProvider(this.credentials, this.optimizations),
                resultCache,
                this.repository,
                this.idClass,
                information.createGlobalSessionCache(),
                sessionCacheSupplier,
                cacheWarmer,
                cacheEnabled,
                maxSize
            );
        }

        return new PostgreSQLRepositoryAdapter<>(
                this.connectionProvider != null
                ? this.connectionProvider.apply(credentials, this.optimizations)
                : new PostgreSQLSimpleConnectionProvider(this.credentials, this.optimizations),
                resultCache,
                this.repository,
                this.idClass,
                null,
                sessionCacheSupplier,
                cacheWarmer,
                cacheEnabled,
                maxSize
        );
    }
}
