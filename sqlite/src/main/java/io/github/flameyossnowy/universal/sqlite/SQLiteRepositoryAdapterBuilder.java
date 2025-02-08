package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.connections.SimpleConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unused")
public class SQLiteRepositoryAdapterBuilder<T> {
    private SQLiteCredentials credentials;
    private BiFunction<SQLiteCredentials, EnumSet<Optimizations>, ConnectionProvider<Connection>> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;

    public SQLiteRepositoryAdapterBuilder(Class<T> repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
    }

    public SQLiteRepositoryAdapterBuilder<T> withConnectionProvider(BiFunction<SQLiteCredentials, EnumSet<Optimizations>, ConnectionProvider<Connection>> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T> withConnectionProvider(Function<SQLiteCredentials, ConnectionProvider<Connection>> connectionProvider) {
        this.connectionProvider = (credentials, optimizations) -> connectionProvider.apply(credentials);
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T> withCredentials(SQLiteCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    public SQLiteRepositoryAdapter<T> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");

        return new SQLiteRepositoryAdapter<>(
                connectionProvider != null
                ? this.connectionProvider.apply(credentials, optimizations)
                : new SimpleConnectionProvider(this.credentials),
                new QueryParseEngine(this.optimizations),
                this.repository
        );
    }
}
