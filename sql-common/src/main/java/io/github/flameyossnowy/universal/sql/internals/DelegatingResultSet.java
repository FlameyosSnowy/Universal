package io.github.flameyossnowy.universal.sql.internals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class DelegatingResultSet extends ResultSetWrapper {
    private final Statement statement;
    private final Connection connection;

    DelegatingResultSet(ResultSet rs, Statement stmt, Connection conn) {
        super(rs);
        this.statement = stmt;
        this.connection = conn;
    }

    @Override
    public void close() throws SQLException {
        try {
            super.close();
        } finally {
            try {
                statement.close();
            } finally {
                connection.close();
            }
        }
    }
}
