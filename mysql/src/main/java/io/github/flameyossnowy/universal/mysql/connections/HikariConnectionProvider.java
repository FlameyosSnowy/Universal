package io.github.flameyossnowy.universal.mysql.connections;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class HikariConnectionProvider implements ConnectionProvider<Connection> {
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(@NotNull MySQLCredentials credentials) {
        HikariConfig config = new HikariConfig();

        if (credentials.ssl()) {
            config.addDataSourceProperty("useSSL", "true");
            config.addDataSourceProperty("requireSSL", "true");
            config.addDataSourceProperty("verifyServerCertificate", "true");
        }

        config.setPassword(credentials.password());
        config.setUsername(credentials.username());
        config.setDriverClassName(credentials.driver());

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
