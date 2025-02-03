package io.github.flameyossnowy.universal.sqlite.connections;

import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;

import java.sql.Connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Map;

public class HikariConnectionProvider implements ConnectionProvider<Connection> {
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(@NotNull SQLiteCredentials credentials) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(credentials.jdbcUrl());

        for (Map.Entry<String, String> entry : credentials.dataSourceProperties().entrySet()) {
            config.addDataSourceProperty(entry.getKey(), entry.getValue());
        }

        config.setMinimumIdle(credentials.minimumIdle());
        config.setIdleTimeout(credentials.idleTimeout());
        config.setConnectionTimeout(credentials.connectionTimeout());
        config.setMaximumPoolSize(credentials.poolSize());

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
