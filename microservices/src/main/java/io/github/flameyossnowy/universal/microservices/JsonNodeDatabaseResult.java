package io.github.flameyossnowy.universal.microservices;
 
import io.github.flameyossnowy.uniform.json.dom.JsonObject;
import io.github.flameyossnowy.uniform.json.dom.JsonValue;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.uniform.json.JsonAdapter;

import java.util.List;
 
/**
 * A {@link DatabaseResult} backed by a Uniform {@link JsonObject}.
 *
 * <p>Used during file-based entity deserialization: after the raw JSON bytes are
 * parsed into a {@code JsonNode}, this wrapper lets {@code GeneratedValueReaders}
 * pull typed field values without knowing anything about the file storage layer.
 *
 * <p>Collection handling is fully delegated to {@link JsonAdapter} so no custom
 * collection-to-Java logic lives here.
 */
public final class JsonNodeDatabaseResult implements DatabaseResult {
 
    private final JsonObject node;
    private final JsonAdapter         objectMapper;
    private final RepositoryModel<?, ?> repositoryModel;
    private final CollectionHandler   collectionHandler;
 
    /** Column name list derived once from the node's field names. */
    private final List<String> columns;
 
    public JsonNodeDatabaseResult(
        JsonObject              node,
        JsonAdapter           objectMapper,
        RepositoryModel<?, ?> repositoryModel
    ) {
        this.node             = node;
        this.objectMapper     = objectMapper;
        this.repositoryModel  = repositoryModel;
        this.collectionHandler = new JsonCollectionHandler(objectMapper, node);
 
        this.columns = List.copyOf(node.keys());
    }
 
    // -------------------------------------------------------------------------
    // DatabaseResult
    // -------------------------------------------------------------------------
 
    @Override
    public CollectionHandler getCollectionHandler() {
        return collectionHandler;
    }
 
    /**
     * File storage never uses native DB arrays; collections are stored as JSON
     * arrays and handled by {@link JsonCollectionHandler}.
     */
    @Override
    public boolean supportsArraysNatively() {
        return false;
    }
 
    @Override
    public RepositoryModel<?, ?> repositoryModel() {
        return repositoryModel;
    }
 
    /**
     * Returns the value of {@code columnName} converted to {@code type}.
     *
     * <p>Delegates to {@link JsonAdapter#readValue(JsonValue, Class)} so every type
     * supported by Uniform (primitives, boxed types, enums, {@code UUID}, temporal
     * types, nested objects, …) works automatically without any switch logic here.
     *
     * @throws IllegalArgumentException if the column is absent or conversion fails
     */
    @Override
    public <T> T get(String columnName, Class<T> type) {
        if (!node.contains(columnName)) {
            throw new IllegalArgumentException(
                "Column '" + columnName + "' not found in JSON node. " +
                "Available columns: " + columns);
        }
 
        JsonValue field = node.getRaw(columnName);
 
        // Null node → return null (the caller must accept null for the given type).
        if (field == null || field.isNull()) {
            return null;
        }
 
        // Delegate all type coercion to Uniform's JsonAdapter.
        return objectMapper.readValue(field, type);
    }
 
    @Override
    public boolean hasColumn(String columnName) {
        return node.contains(columnName);
    }
 
    @Override
    public int getColumnCount() {
        return columns.size();
    }
 
    /**
     * @param columnIndex 1-based column index (matches JDBC convention used by
     *                    most {@code DatabaseResult} consumers).
     */
    @Override
    public String getColumnName(int columnIndex) {
        if (columnIndex < 1 || columnIndex > columns.size()) {
            throw new IndexOutOfBoundsException(
                "Column index " + columnIndex + " is out of bounds [1, " + columns.size() + "]");
        }
        return columns.get(columnIndex - 1);
    }
 
    // -------------------------------------------------------------------------
    // Package-visible extras (useful for debugging / testing)
    // -------------------------------------------------------------------------
 
    /** Returns the raw {@link JsonObject} this result wraps. */
    public JsonObject rawNode() {
        return node;
    }
}
 