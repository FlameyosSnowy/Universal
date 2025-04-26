package io.github.flameyossnowy.universal.mysql.connections;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;

@SuppressWarnings("unused")
public class MySQLSimpleConnectionProvider implements SQLConnectionProvider {
    protected final MysqlDataSource dataSource;

    public MySQLSimpleConnectionProvider(final @NotNull MySQLCredentials credentials, final EnumSet<Optimizations> optimizations) {
        this.dataSource = new MysqlDataSource();
        dataSource.setPassword(credentials.getPassword());
        try {
            if (credentials.isSsl()) {
                dataSource.setUseSSL(true);
                dataSource.setRequireSSL(true);
                dataSource.setVerifyServerCertificate(true);
            } else {
                dataSource.setUseSSL(false);
                dataSource.setRequireSSL(false);
                dataSource.setVerifyServerCertificate(false);
            }

            dataSource.setAllowPublicKeyRetrieval(true);
            dataSource.setPassword(credentials.getPassword());
            dataSource.setUser(credentials.getUsername());
            dataSource.setDatabaseName(credentials.getDatabase());
            if (optimizations.contains(Optimizations.CACHE_PREPARED_STATEMENTS)) {
                dataSource.setCachePrepStmts(true);
            }
            if (optimizations.contains(Optimizations.RECOMMENDED_SETTINGS)) {
                dataSource.setCachePrepStmts(true);
                dataSource.setPrepStmtCacheSize(250);
                dataSource.setPrepStmtCacheSqlLimit(2048);
                dataSource.setUseServerPrepStmts(true);
                dataSource.setRewriteBatchedStatements(true);
                dataSource.setUseLocalSessionState(true);
                dataSource.setCacheResultSetMetadata(true);
                dataSource.setElideSetAutoCommits(true);
                dataSource.setMaintainTimeStats(false);
            }
            credentials.getDataSourceConsumer().accept(dataSource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {

    }

    @Override
    public PreparedStatement prepareStatement(String sql, Connection connection) throws Exception {
        return connection.prepareStatement(sql);
    }
}
