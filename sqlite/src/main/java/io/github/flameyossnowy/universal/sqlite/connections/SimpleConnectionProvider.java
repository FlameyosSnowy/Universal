package io.github.flameyossnowy.universal.sqlite.connections;

import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class SimpleConnectionProvider implements ConnectionProvider<Connection> {
    private Connection connection;
    private String url;

    public SimpleConnectionProvider(final SQLiteCredentials credentials) {
        StringBuilder url = new StringBuilder(credentials.jdbcUrl());
        boolean isFirst = true;

        for (Map.Entry<String, String> entry : credentials.dataSourceProperties().entrySet()) {
            if (isFirst) {
                url.append('?');
                isFirst = false;
            } else {
                url.append('&');
            }
            url.append(entry.getKey()).append('=').append(entry.getValue());
        }
        this.url = url.toString();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    @Override
    public void close() {
    }
}
