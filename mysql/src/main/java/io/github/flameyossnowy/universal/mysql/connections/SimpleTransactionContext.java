package io.github.flameyossnowy.universal.mysql.connections;

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
    public void commit() throws SQLException {
        connection.commit();
        commited = true;
    }

    @Override
    public void rollback() throws SQLException {
        if (!commited) connection.rollback();
    }

    @Override
    public void close() throws Exception {
        rollback();
        connection.close();
    }
}
