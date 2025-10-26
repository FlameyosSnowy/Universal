package io.github.flameyossnow.universal.cassandra.objects;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.Row;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

import java.util.*;

/**
 * A database-agnostic result implementation for Cassandra that uses TypeResolverRegistry
 * for type conversion.
 */
public class CassandraDatabaseResult implements DatabaseResult {
    private final Row row;
    private final ColumnDefinitions columnDefinitions;

    /**
     * Creates a new CassandraDatabaseResult with a custom TypeResolverRegistry.
     *
     * @param row the Cassandra row to wrap
     * @param ignored the TypeResolverRegistry to use for type conversion
     */
    public CassandraDatabaseResult(Row row, TypeResolverRegistry ignored) {
        this.row = Objects.requireNonNull(row, "Row cannot be null");
        this.columnDefinitions = row.getColumnDefinitions();
    }

    @Override
    public <T> T get(String columnName, Class<T> type) {
        if (columnName == null) {
            throw new IllegalArgumentException("Column name cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        return row.get(columnName, type);
    }

    @Override
    public boolean hasColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        return columnDefinitions.contains(columnName);
    }

    @Override
    public int getColumnCount() {
        return columnDefinitions.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= getColumnCount()) {
            throw new IndexOutOfBoundsException("Column index out of bounds: " + columnIndex);
        }
        return columnDefinitions.getName(columnIndex);
    }
}
