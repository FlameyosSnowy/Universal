package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.sql.internals.ResultCache;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.connections.SQLiteSimpleConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unused")
public class SQLiteRepositoryAdapterBuilder<T, ID> {
    private SQLiteCredentials credentials;
    private BiFunction<SQLiteCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;
    private final Class<ID> idClass;

    public SQLiteRepositoryAdapterBuilder(Class<T> repository, Class<ID> id) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.idClass = Objects.requireNonNull(id, "ID class cannot be null");
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withConnectionProvider(BiFunction<SQLiteCredentials, EnumSet<Optimizations>, SQLConnectionProvider> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T, ID> withConnectionProvider(Function<SQLiteCredentials, SQLConnectionProvider> connectionProvider) {
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

        Cacheable cacheable = RepositoryMetadata.getMetadata(this.repository).getCacheable();

        ResultCache<T, ID> cache = cacheable != null ? new ResultCache<>(cacheable.maxCacheSize(), cacheable.algorithm()) : null;

        return new SQLiteRepositoryAdapter<>(
                connectionProvider != null ? this.connectionProvider.apply(credentials, optimizations) : new SQLiteSimpleConnectionProvider(this.credentials, optimizations),
                cache, this.repository, this.idClass
        );
    }
}
