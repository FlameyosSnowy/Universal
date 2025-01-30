package me.flame.universal.mysql.connections;

import me.flame.universal.api.connection.ConnectionProvider;
import me.flame.universal.mysql.credentials.MySQLCredentials;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class SimpleConnectionProvider implements ConnectionProvider<Connection> {
    private Connection connection;

    public SimpleConnectionProvider(final MySQLCredentials credentials) {
        try {
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
            this.connection = DriverManager.getConnection(url.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
        this.connection = null;
    }
}
