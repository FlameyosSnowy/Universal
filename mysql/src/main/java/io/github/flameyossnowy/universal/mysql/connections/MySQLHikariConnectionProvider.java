package io.github.flameyossnowy.universal.mysql.connections;

import com.zaxxer.hikari.HikariDataSource;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;

@SuppressWarnings("unused")
public class MySQLHikariConnectionProvider extends MySQLSimpleConnectionProvider implements SQLConnectionProvider {
    private final HikariDataSource pool;

    public MySQLHikariConnectionProvider(@NotNull MySQLCredentials credentials, EnumSet<Optimizations> optimizations) {
        super(credentials, optimizations);
        this.pool = new HikariDataSource();
        pool.setDataSource(dataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return pool.getConnection();
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
