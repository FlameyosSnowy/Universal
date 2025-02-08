package io.github.flameyossnowy.universal.mysql.connections;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;

@SuppressWarnings("unused")
public class HikariConnectionProvider implements ConnectionProvider<Connection> {
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(@NotNull MySQLCredentials credentials) {
        HikariConfig config = new HikariConfig();

        defaultConfig(credentials, config);

        this.dataSource = new HikariDataSource(config);
    }

    public HikariConnectionProvider(@NotNull MySQLCredentials credentials, @NotNull EnumSet<Optimizations> optimizations) {
        HikariConfig config = new HikariConfig();

        if (optimizations.contains(Optimizations.RECOMMENDED_SETTINGS)) {
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
        }
        defaultConfig(credentials, config);

        this.dataSource = new HikariDataSource(config);
    }

    private static void defaultConfig(final @NotNull MySQLCredentials credentials, final HikariConfig config) {
        if (credentials.ssl()) {
            config.addDataSourceProperty("useSSL", "true");
            config.addDataSourceProperty("requireSSL", "true");
            config.addDataSourceProperty("verifyServerCertificate", "true");
        } else {
            config.addDataSourceProperty("useSSL", "false");
            config.addDataSourceProperty("requireSSL", "false");
            config.addDataSourceProperty("verifyServerCertificate", "false");
        }

        config.setPassword(credentials.password());
        config.setUsername(credentials.username());
        config.addDataSourceProperty("databaseName", credentials.database()); // Set database here        //config.setDriverClassName(credentials.driver());
        // mysql datasource class name
        config.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");

        for (Map.Entry<String, String> entry : credentials.dataSourceProperties().entrySet()) {
            config.addDataSourceProperty(entry.getKey(), entry.getValue());
        }

        config.setMinimumIdle(credentials.minimumIdle());
        config.setIdleTimeout(credentials.idleTimeout());
        config.setConnectionTimeout(credentials.connectionTimeout());
        config.setMaximumPoolSize(credentials.poolSize());
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
