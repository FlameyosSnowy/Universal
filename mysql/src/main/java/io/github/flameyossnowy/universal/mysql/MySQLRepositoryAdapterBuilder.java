package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.DefaultSessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.mysql.connections.MySQLSimpleConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

@SuppressWarnings("unused")
public class MySQLRepositoryAdapterBuilder<T, ID> {
    static {
        ModelsBootstrap.init();
    }

    private MySQLCredentials credentials;
    private BiFunction<MySQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new DefaultSessionCache<>();
    private CacheWarmer<T, ID> cacheWarmer;

    public MySQLRepositoryAdapterBuilder(Class<T> repository, Class<ID> idClass) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(idClass, "Repository cannot be null");
    }

    public MySQLRepositoryAdapterBuilder<T, ID> withCacheWarmer(CacheWarmer<T, ID> cacheWarmer) {
        this.cacheWarmer = cacheWarmer;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T, ID> withConnectionProvider(BiFunction<MySQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T, ID> withCredentials(MySQLCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T, ID> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T, ID> setSessionCacheSupplier(LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T, ID> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    @SuppressWarnings("unchecked")
    public MySQLRepositoryAdapter<T, ID> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");
        RepositoryModel<T, ID> information = Objects.requireNonNull(GeneratedMetadata.getByEntityClass(this.repository));

        CacheConfig cacheConfig = information.getCacheConfig();
        boolean globalCacheable = information.isGlobalCacheable();

        boolean cacheEnabled = cacheConfig != null;
        int maxSize = 0;

        DefaultResultCache<String, T, ID> resultCache = null;

        if (cacheEnabled) {
            maxSize = cacheConfig.maxSize();
            resultCache = new DefaultResultCache<>(maxSize, cacheConfig.cacheAlgorithmType());
        }

        return new MySQLRepositoryAdapter<>(
            this.connectionProvider != null ? this.connectionProvider.apply(credentials, this.optimizations) : new MySQLSimpleConnectionProvider(this.credentials, this.optimizations),
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
}
