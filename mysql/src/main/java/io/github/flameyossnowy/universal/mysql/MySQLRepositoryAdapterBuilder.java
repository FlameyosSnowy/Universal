package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.mysql.connections.SimpleConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public class MySQLRepositoryAdapterBuilder<T> {
    private MySQLCredentials credentials;
    private BiFunction<MySQLCredentials, EnumSet<Optimizations>, ConnectionProvider<Connection>> connectionProvider;
    private final EnumSet<Optimizations> optimizations = EnumSet.noneOf(Optimizations.class);
    private final Class<T> repository;

    public MySQLRepositoryAdapterBuilder(Class<T> repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
    }

    public MySQLRepositoryAdapterBuilder<T> withConnectionProvider(BiFunction<MySQLCredentials, EnumSet<Optimizations>, ConnectionProvider<Connection>> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T> withCredentials(MySQLCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T> withOptimizations(Optimizations... optimizations) {
        Collections.addAll(this.optimizations, optimizations);
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T> withOptimizations(Collection<Optimizations> optimizations) {
        this.optimizations.addAll(optimizations);
        return this;
    }

    public MySQLRepositoryAdapter<T> build() {
        if (this.credentials == null) throw new IllegalArgumentException("Credentials cannot be null");

        return new MySQLRepositoryAdapter<>(
                this.connectionProvider != null
                ? this.connectionProvider.apply(credentials, this.optimizations)
                : new SimpleConnectionProvider(this.credentials, this.optimizations),
                new QueryParseEngine(this.optimizations),
                this.repository
        );
    }
}
