package io.github.flameyossnowy.universal.mongodb.result;

import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.bson.Document;

/**
 * MongoDB implementation of DatabaseResult that wraps a BSON Document.
 */
public class MongoDatabaseResult implements DatabaseResult {
    private final Document document;
    private String[] columnNames;

    public MongoDatabaseResult(Document document) {
        this.document = document;
    }

    private String[] getColumnNames() {
        if (columnNames == null) {
            columnNames = document.keySet().toArray(new String[0]);
        }
        return columnNames;
    }

    @Override
    public <T> T get(String fieldName, Class<T> type) {
        if (document == null) return null;
        return document.get(fieldName, type);
    }

    @Override
    public boolean hasColumn(String columnName) {
        return document != null && document.containsKey(columnName);
    }

    @Override
    public int getColumnCount() {
        return document != null ? document.size() : 0;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return document != null ? this.getColumnNames()[columnIndex] : null;
    }
}
