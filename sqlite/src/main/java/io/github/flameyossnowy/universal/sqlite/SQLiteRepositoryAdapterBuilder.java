package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.cache.ResultCache;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sqlite.connections.SimpleConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;
import io.github.flameyossnowy.universal.sql.QueryParseEngine;

import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unused")
public class SQLiteRepositoryAdapterBuilder<T, ID> {
    private SQLiteCredentials credentials;
    private BiFunction<SQLiteCredentials, EnumSet<Optimizations>, ConnectionProvider<Connection>> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;

    public SQLiteRepositoryAdapterBuilder(Class<T> repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withConnectionProvider(BiFunction<SQLiteCredentials, EnumSet<Optimizations>, ConnectionProvider<Connection>> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withConnectionProvider(Function<SQLiteCredentials, ConnectionProvider<Connection>> connectionProvider) {
        this.connectionProvider = (credentials, optimizations) -> connectionProvider.apply(credentials);
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withCredentials(SQLiteCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    public SQLiteRepositoryAdapter<T, ID> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");

        Cacheable cacheable = RepositoryMetadata.getMetadata(this.repository).cacheable();

        return new SQLiteRepositoryAdapter<>(
                connectionProvider != null
                ? this.connectionProvider.apply(credentials, optimizations)
                : new SimpleConnectionProvider(this.credentials),
                cacheable != null
                        ? new ResultCache(cacheable.maxCacheSize(), cacheable.algorithm())
                        : null,
                this.optimizations,
                this.repository
        );
    }
}
