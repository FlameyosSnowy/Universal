package io.github.flameyossnowy.universal.postgresql.connections;

import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.postgresql.jdbc.PreferQueryMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Properties;

@SuppressWarnings("unused")
public class PostgreSQLSimpleConnectionProvider implements SQLConnectionProvider {
    protected final PGSimpleDataSource dataSource;

    public PostgreSQLSimpleConnectionProvider(final @NotNull PostgreSQLCredentials credentials, final EnumSet<Optimizations> optimizations) {
        this.dataSource = new PGSimpleDataSource();
        dataSource.setPassword(credentials.getPassword());
        try {
            dataSource.setSsl(credentials.isSsl());

            dataSource.setPassword(credentials.getPassword());
            dataSource.setUser(credentials.getUsername());
            dataSource.setDatabaseName(credentials.getDatabase());
            if (optimizations.contains(Optimizations.CACHE_PREPARED_STATEMENTS)) {
                dataSource.setProperty(PGProperty.PREPARED_STATEMENT_CACHE_QUERIES, "300");
            }
            if (optimizations.contains(Optimizations.RECOMMENDED_SETTINGS)) {

                Properties props = new Properties();
                PGProperty.TCP_KEEP_ALIVE.set(props, true);                 // Prevents broken idle connections
                PGProperty.REWRITE_BATCHED_INSERTS.set(props, true);        // Optimizes batch inserts
                PGProperty.APPLICATION_NAME.set(props, "Universal PostgreSQL Application");   // Shows up in pg_stat_activity
                PGProperty.PREPARE_THRESHOLD.set(props, 5);                 // Enable server-side prepared statements
                PGProperty.LOGIN_TIMEOUT.set(props, 5);                     // Login timeout in seconds
                PGProperty.CONNECT_TIMEOUT.set(props, 5);                   // Connection timeout in seconds
                PGProperty.SOCKET_TIMEOUT.set(props, 30);                   // Socket read timeout
                PGProperty.TCP_NO_DELAY.set(props, true);                   // Disable Nagle's algorithm
                PGProperty.PREFER_QUERY_MODE.set(props, PreferQueryMode.EXTENDED_CACHE_EVERYTHING.value()); // Use extended cache
                PGProperty.PREPARED_STATEMENT_CACHE_QUERIES.set(props, 300); // Cache prepared statements

                for (String key : props.stringPropertyNames()) {
                    dataSource.setProperty(key, props.getProperty(key));
                }
            }
            credentials.getDataSourceConsumer().accept(dataSource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

    }

    @Override
    public PreparedStatement prepareStatement(String sql, Connection connection) throws Exception {
        return connection.prepareStatement(sql);
    }
}
