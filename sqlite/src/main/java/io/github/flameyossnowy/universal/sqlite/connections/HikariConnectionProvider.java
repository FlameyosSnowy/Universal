package io.github.flameyossnowy.universal.sqlite.connections;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;

import java.sql.Connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteDataSource;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;

public class HikariConnectionProvider implements ConnectionProvider<Connection> {
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(@NotNull SQLiteCredentials credentials, EnumSet<Optimizations> optimizations) {
        HikariConfig config = new HikariConfig();

        SQLiteDataSource dataSource = new SQLiteDataSource();
        if (optimizations.contains(Optimizations.RECOMMENDED_SETTINGS)) {
            dataSource.setSynchronous("NORMAL");
            dataSource.setJournalMode("WAL");
            dataSource.setCacheSize(10000);
        }
        config.setDataSource(dataSource);

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
