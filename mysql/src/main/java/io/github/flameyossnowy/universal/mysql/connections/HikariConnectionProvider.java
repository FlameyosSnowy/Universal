package io.github.flameyossnowy.universal.mysql.connections;

import com.mysql.cj.jdbc.MysqlDataSource;
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

        MysqlDataSource mysqlDataSource = new MysqlDataSource();
        defaultConfig(credentials, config, mysqlDataSource);

        this.dataSource = new HikariDataSource(config);
    }

    public HikariConnectionProvider(@NotNull MySQLCredentials credentials, @NotNull EnumSet<Optimizations> optimizations) {
        HikariConfig config = new HikariConfig();

        MysqlDataSource mysqlDataSource = new MysqlDataSource();
        if (optimizations.contains(Optimizations.RECOMMENDED_SETTINGS)) {
            try {
                addRecommendedPerformanceSettings(mysqlDataSource);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        }
        defaultConfig(credentials, config, mysqlDataSource);

        this.dataSource = new HikariDataSource(config);
    }

    private static void addRecommendedPerformanceSettings(final @NotNull MysqlDataSource mysqlDataSource) throws SQLException {
        mysqlDataSource.setCachePrepStmts(true);
        mysqlDataSource.setPrepStmtCacheSize(250);
        mysqlDataSource.setPrepStmtCacheSqlLimit(2048);
        mysqlDataSource.setUseServerPrepStmts(true);
        mysqlDataSource.setRewriteBatchedStatements(true);
        mysqlDataSource.setUseLocalSessionState(true);
        mysqlDataSource.setCacheResultSetMetadata(true);
        mysqlDataSource.setElideSetAutoCommits(true);
        mysqlDataSource.setMaintainTimeStats(false);
    }

    private static void defaultConfig(final @NotNull MySQLCredentials credentials, final HikariConfig config, final MysqlDataSource dataSource) {
        try {
            if (credentials.ssl()) {
                dataSource.setUseSSL(true);
                dataSource.setRequireSSL(true);
                dataSource.setVerifyServerCertificate(true);
            } else {
                dataSource.setUseSSL(false);
                dataSource.setRequireSSL(false);
                dataSource.setVerifyServerCertificate(false);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        dataSource.setPassword(credentials.password());
        dataSource.setUser(credentials.username());
        dataSource.setDatabaseName(credentials.database());

        config.setDataSource(dataSource);
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
