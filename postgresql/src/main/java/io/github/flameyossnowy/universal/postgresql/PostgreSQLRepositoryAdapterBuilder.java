package io.github.flameyossnowy.universal.postgresql;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.postgresql.connections.PostgreSQLSimpleConnectionProvider;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import io.github.flameyossnowy.universal.sql.SQLSessionCache;
import io.github.flameyossnowy.universal.sql.internals.ResultCache;
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
    private PostgreSQLCredentials credentials;
    private BiFunction<PostgreSQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;

    private LongFunction<SessionCache<ID, T>> sessionCacheSupplier = (id) -> new SQLSessionCache<>();

    public PostgreSQLRepositoryAdapterBuilder(Class<T> repository, Class<ID> idClass) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(idClass, "Repository cannot be null");
    }

    public PostgreSQLRepositoryAdapterBuilder<T, ID> withConnectionProvider(BiFunction<PostgreSQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider) {
        this.connectionProvider = connectionProvider;
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
        RepositoryInformation information = Objects.requireNonNull(RepositoryMetadata.getMetadata(this.repository));

        Cacheable cacheable = information.getCacheable();

        GlobalCacheable globalCacheable = information.getGlobalCacheable();

        ResultCache<T, ID> resultCache = cacheable != null ? new ResultCache<>(cacheable.maxCacheSize(), cacheable.algorithm()) : null;

        if (globalCacheable != null) {
            try {
                return new PostgreSQLRepositoryAdapter<>(
                        this.connectionProvider != null ? this.connectionProvider.apply(credentials, this.optimizations) : new PostgreSQLSimpleConnectionProvider(this.credentials, this.optimizations),
                        resultCache,
                        this.repository,
                        this.idClass,
                        (SessionCache<ID, T>) globalCacheable.sessionCache().getDeclaredConstructor().newInstance(),
                        sessionCacheSupplier
                );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        return new PostgreSQLRepositoryAdapter<>(
                this.connectionProvider != null
                ? this.connectionProvider.apply(credentials, this.optimizations)
                : new PostgreSQLSimpleConnectionProvider(this.credentials, this.optimizations),
                cacheable != null ? new ResultCache<>(cacheable.maxCacheSize(), cacheable.algorithm()) : null,
                this.repository,
                this.idClass,
                null,
                sessionCacheSupplier
        );
    }
}
