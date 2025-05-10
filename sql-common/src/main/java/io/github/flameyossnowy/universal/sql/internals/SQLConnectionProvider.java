package io.github.flameyossnowy.universal.sql.internals;

import org.jetbrains.annotations.ApiStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;

@ApiStatus.Internal
public interface SQLConnectionProvider extends AutoCloseable {
    /**
     * Get a connection from the provider.
     * <p>
     * This method is thread-safe, and it may return a pooled connection if it uses specific connection providers.
     * <p>
     * @return A connection, or throw an exception if the connection cannot be acquired.
     */
    Connection getConnection();

    /**
     * Close the connection provider.
     */
    @Override
    void close();

    /**
     * Prepares a SQL statement with the given connection.
     * @param sql the SQL to prepare
     * @param connection the connection to use
     * @return the prepared statement
     * @throws Exception if an error occurs
     */
    PreparedStatement prepareStatement(String sql, Connection connection) throws Exception;
}
