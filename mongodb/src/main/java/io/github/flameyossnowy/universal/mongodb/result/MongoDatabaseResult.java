package io.github.flameyossnowy.universal.mongodb.result;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.bson.Document;
import org.jetbrains.annotations.Nullable;

/**
 * MongoDB implementation of DatabaseResult that wraps a BSON Document.
 */
public class MongoDatabaseResult implements DatabaseResult {
    private final Document document;
    private final CollectionHandler collectionHandler;
    private final RepositoryModel<?, ?> repositoryModel;
    private String[] columnNames;

    private static Class<?> wrapPrimitiveType(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    public MongoDatabaseResult(Document document, CollectionHandler collectionHandler, RepositoryModel<?, ?> repositoryModel) {
        this.document = document;
        this.collectionHandler = collectionHandler;
        this.repositoryModel = repositoryModel;
    }

    private String[] getColumnNamesLazy() {
        if (columnNames == null) {
            columnNames = document.keySet().toArray(new String[0]);
        }
        return columnNames;
    }

    @Override
    public CollectionHandler getCollectionHandler() {
        return collectionHandler;
    }

    @Override
    public boolean supportsArraysNatively() {
        return false;
    }

    @Override
    public RepositoryModel<?, ?> repositoryModel() {
        return repositoryModel;
    }

    @Override
    public <T> @Nullable T get(String fieldName, Class<T> type) {
        if (document == null) return null;
        @SuppressWarnings("unchecked")
        Class<T> wrappedType = (Class<T>) wrapPrimitiveType(type);
        return document.get(fieldName, wrappedType);
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
    public @Nullable String getColumnName(int columnIndex) {
        return document != null ? this.getColumnNamesLazy()[columnIndex] : null;
    }
}
