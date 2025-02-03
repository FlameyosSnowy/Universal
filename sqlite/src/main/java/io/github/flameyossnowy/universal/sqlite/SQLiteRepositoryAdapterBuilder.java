package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.connections.SimpleConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.sql.Connection;

public class SQLiteRepositoryAdapterBuilder<T> {
    private SQLiteCredentials credentials;
    private ConnectionProvider<Connection> connectionProvider;
    private final Class<T> repository;

    public SQLiteRepositoryAdapterBuilder(Class<T> repository) {
        this.repository = repository;
    }

    public SQLiteRepositoryAdapterBuilder<T> withConnectionProvider(ConnectionProvider<Connection> connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public SQLiteRepositoryAdapterBuilder<T> withCredentials(SQLiteCredentials credentials) {
        this.credentials = credentials;
        return this;
    }

    public SQLiteRepositoryAdapter<T> build() {
        if (this.credentials == null) {
            throw new IllegalArgumentException("Credentials cannot be null");
        }

        if (this.connectionProvider == null) {
            this.connectionProvider = new SimpleConnectionProvider(this.credentials);
        }
        return SQLiteRepositoryAdapter.open(this.credentials, this.repository);
    }
}
