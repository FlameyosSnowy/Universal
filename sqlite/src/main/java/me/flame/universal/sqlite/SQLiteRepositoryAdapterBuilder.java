package me.flame.universal.sqlite;

import me.flame.universal.api.connection.ConnectionProvider;
import me.flame.universal.sqlite.connections.SimpleConnectionProvider;
import me.flame.universal.sqlite.credentials.SQLiteCredentials;
import org.jetbrains.annotations.NotNull;

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
