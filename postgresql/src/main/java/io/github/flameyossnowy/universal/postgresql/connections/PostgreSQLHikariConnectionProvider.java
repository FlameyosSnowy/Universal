package io.github.flameyossnowy.universal.postgresql.connections;

import com.zaxxer.hikari.HikariDataSource;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;

@SuppressWarnings("unused")
public class PostgreSQLHikariConnectionProvider extends PostgreSQLSimpleConnectionProvider implements SQLConnectionProvider {
    private final HikariDataSource pool;

    public PostgreSQLHikariConnectionProvider(@NotNull PostgreSQLCredentials credentials, EnumSet<Optimizations> optimizations) {
        super(credentials, optimizations);
        this.pool = new HikariDataSource();
        pool.setDataSource(dataSource);
    }

    @Override
    public Connection getConnection() {
        try {
            return pool.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        pool.close();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, @NotNull Connection connection) throws Exception {
        return connection.prepareStatement(sql);
    }
}
