package io.github.flameyossnowy.universal.sqlite.connections;

import io.github.flameyossnowy.universal.api.Optimizations;

import java.sql.Connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;

public class SQLiteHikariConnectionProvider extends SQLiteSimpleConnectionProvider implements SQLConnectionProvider {
    private final HikariDataSource hikariDataSource;

    public SQLiteHikariConnectionProvider(@NotNull SQLiteCredentials credentials, @NotNull EnumSet<Optimizations> optimizations) {
        super(credentials, optimizations);
        HikariConfig config = new HikariConfig();
        config.setDataSource(dataSource);

        for (Map.Entry<String, String> entry : credentials.getDataSourceProperties().entrySet()) {
            config.addDataSourceProperty(entry.getKey(), entry.getValue());
        }

        config.setMinimumIdle(credentials.getMinimumIdle());
        config.setIdleTimeout(credentials.getIdleTimeout());
        config.setConnectionTimeout(credentials.getConnectionTimeout());
        config.setMaximumPoolSize(credentials.getPoolSize());

        this.hikariDataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() {
        try {
            return hikariDataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        hikariDataSource.close();
    }
}
