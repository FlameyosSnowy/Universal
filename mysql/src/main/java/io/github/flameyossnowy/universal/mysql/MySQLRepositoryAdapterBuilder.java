package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.sql.internals.ResultCache;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.mysql.connections.MySQLSimpleConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public class MySQLRepositoryAdapterBuilder<T, ID> {
    private MySQLCredentials credentials;
    private BiFunction<MySQLCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;

    public MySQLRepositoryAdapterBuilder(Class<T> repository, Class<ID> idClass) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(idClass, "Repository cannot be null");
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

    public MySQLRepositoryAdapterBuilder<T, ID> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    public MySQLRepositoryAdapter<T, ID> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");

        RepositoryInformation information = RepositoryMetadata.getMetadata(this.repository);
        Cacheable cacheable = information.getCacheable();
        return new MySQLRepositoryAdapter<>(
                this.connectionProvider != null
                ? this.connectionProvider.apply(credentials, this.optimizations)
                : new MySQLSimpleConnectionProvider(this.credentials, this.optimizations),
                cacheable != null ? new ResultCache<>(cacheable.maxCacheSize(), cacheable.algorithm()) : null,
                this.repository,
                this.idClass
        );
    }
}
