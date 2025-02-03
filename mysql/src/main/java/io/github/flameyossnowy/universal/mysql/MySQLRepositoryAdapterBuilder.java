package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.mysql.connections.SimpleConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.sql.Connection;
import java.util.Objects;

public class MySQLRepositoryAdapterBuilder<T> {
    private MySQLCredentials credentials;
    private ConnectionProvider<Connection> connectionProvider;
    private final Class<T> repository;

    public MySQLRepositoryAdapterBuilder(Class<T> repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
    }

    public MySQLRepositoryAdapterBuilder<T> withConnectionProvider(ConnectionProvider<Connection> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public MySQLRepositoryAdapterBuilder<T> withCredentials(MySQLCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public MySQLRepositoryAdapter<T> build() {
        if (this.credentials == null) {
            throw new IllegalArgumentException("Credentials cannot be null");
        }

        if (this.connectionProvider == null) {
            this.connectionProvider = new SimpleConnectionProvider(this.credentials);
        }
        return MySQLRepositoryAdapter.open(this.connectionProvider, this.repository);
    }
}
