package io.github.flameyossnowy.universal.sqlite.connections;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SQLiteSimpleConnectionProvider implements SQLConnectionProvider {
    protected final SQLiteDataSource dataSource;

    private final Map<String, PreparedStatement> preparedStatements;

    private final boolean cachePreparedStatements;

    public SQLiteSimpleConnectionProvider(final SQLiteCredentials credentials, EnumSet<Optimizations> optimizations) {
        StringBuilder url = new StringBuilder(credentials.getJdbcUrl());
        boolean isFirst = true;

        if (optimizations.contains(Optimizations.CACHE_PREPARED_STATEMENTS)) {
            this.cachePreparedStatements = true;
            this.preparedStatements = new ConcurrentHashMap<>();
        } else {
            this.cachePreparedStatements = false;
            this.preparedStatements = null;
        }

        for (Map.Entry<String, String> entry : credentials.getDataSourceProperties().entrySet()) {
            if (isFirst) {
                url.append('?');
                isFirst = false;
            } else {
                url.append('&');
            }
            url.append(entry.getKey()).append('=').append(entry.getValue());
        }

        this.dataSource = new SQLiteDataSource();
        this.dataSource.setUrl(url.toString());
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

    @SuppressWarnings("SqlSourceToSinkFlow") // Don't worry, we ONLY ever use prepared statements and in the correct way :)
    @Override
    public PreparedStatement prepareStatement(String sql, Connection connection) throws Exception {
        if (!cachePreparedStatements) {
            return connection.prepareStatement(sql);
        }

        PreparedStatement statement = this.preparedStatements.get(sql);
        if (statement == null) {
            statement = connection.prepareStatement(sql);
            this.preparedStatements.put(sql, statement);
        }

        return statement;
    }
}
