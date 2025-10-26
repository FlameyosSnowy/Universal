package io.github.flameyossnowy.universal.sql.result;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQL implementation of DatabaseResult that wraps a JDBC ResultSet and uses DataHandlers
 * for type conversion.
 */
public record SQLDatabaseResult(ResultSet resultSet, TypeResolverRegistry typeRegistry) implements DatabaseResult {
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String columnName, Class<T> type) {
        try {
            if (resultSet.wasNull()) {
                return null;
            }

            // Get the appropriate data handler
            DataHandler<T> handler = typeRegistry.getHandler(type);
            if (handler != null) {
                return handler.fromDatabase(this, columnName);
            }

            // Fall back to direct JDBC access for unhandled types
            return (T) resultSet.getObject(columnName);

        } catch (SQLException e) {
            throw new RuntimeException("Error getting value from result set", e);
        }
    }

    @Override
    public boolean hasColumn(String columnName) {
        try {
            resultSet.findColumn(columnName);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public int getColumnCount() {
        try {
            return resultSet.getMetaData().getColumnCount();
        } catch (SQLException e) {
            throw new RuntimeException("Error getting column count", e);
        }
    }

    @Override
    public String getColumnName(int columnIndex) {
        try {
            return resultSet.getMetaData().getColumnName(columnIndex + 1);
        } catch (SQLException e) {
            throw new RuntimeException("Error getting column name for index: " + columnIndex, e);
        }
    }
}
