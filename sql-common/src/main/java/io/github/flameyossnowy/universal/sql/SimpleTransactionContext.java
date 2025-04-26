package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

import java.sql.Connection;
import java.sql.SQLException;

public class SimpleTransactionContext implements TransactionContext<Connection> {
    private final Connection connection;
    private boolean commited = false;

    public SimpleTransactionContext(Connection connection) throws SQLException {
        this.connection = connection;
        this.connection.setAutoCommit(false);
    }

    @Override
    public Connection connection() {
        return connection;
    }

    @Override
    public TransactionResult<Void> commit() {
        if (commited) return null;
        try {
            connection.commit();
        } catch (SQLException e) {
            return TransactionResult.failure(e);
        }
        commited = true;
        return TransactionResult.success(null);
    }

    @Override
    public void rollback() throws SQLException {
        if (!commited && connection != null && !connection.isClosed()) connection.rollback();
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                if (!commited) connection.rollback();
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}