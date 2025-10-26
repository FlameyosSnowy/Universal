package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

@SuppressWarnings("unused")
public class MultiMapTypeResolver<K, V, ID> {
    private final String tableName;

    private final TypeResolver<K> keyResolver;
    private final TypeResolver<V> valueResolver;
    private final TypeResolver<ID> idResolver;

    private final TypeResolverRegistry resolverRegistry;

    private final SQLConnectionProvider connectionProvider;
    private final RepositoryInformation information;

    public MultiMapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                                final SQLConnectionProvider connectionProvider,
                                final @NotNull RepositoryInformation information,
                                final @NotNull TypeResolverRegistry resolverRegistry) {
        this.connectionProvider = connectionProvider;
        this.information = information;
        this.resolverRegistry = resolverRegistry;

        this.tableName = information.getRepositoryName() + "_" + valueType.getSimpleName().toLowerCase() + "_map";
        this.keyResolver = resolverRegistry.getResolver(keyType);
        this.valueResolver = resolverRegistry.getResolver(valueType);
        this.idResolver = resolverRegistry.getResolver(idType);

        if (keyResolver == null || valueResolver == null || idResolver == null) {
            throw new IllegalStateException("No resolver found for one of the types: " + keyType.getSimpleName() + ", " + valueType.getSimpleName() + ", or " + idType.getSimpleName());
        }
        ensureTableExists();
    }

    private void ensureTableExists() {
        String query = String.format("""
        CREATE TABLE IF NOT EXISTS %s (
            id %s NOT NULL,
            map_key %s NOT NULL,
            map_value %s NOT NULL,
            FOREIGN KEY (id) REFERENCES %s (id)
        );
        """, tableName,
                resolverRegistry.getType(idResolver),
                resolverRegistry.getType(keyResolver),
                resolverRegistry.getType(valueResolver),
                information.getRepositoryName());

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, conn)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create map table: " + tableName, e);
        }
    }

    public Map<K, List<V>> resolve(ID id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE id = ?;")) {
            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry);
            idResolver.insert(params, "id", id);
            ResultSet resultSet = stmt.executeQuery();

            int fetchSize = stmt.getFetchSize();
            Map<K, List<V>> map = new HashMap<>(fetchSize);
            while (resultSet.next()) {
                SQLDatabaseResult result = new SQLDatabaseResult(resultSet, resolverRegistry);
                K key = keyResolver.resolve(result, "map_key");
                V value = valueResolver.resolve(result, "map_value");

                map.computeIfAbsent(key, k -> new ArrayList<>(fetchSize)).add(value);
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Map<K, List<V>> map) throws Exception {
        String insertQuery = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(insertStmt, resolverRegistry);
            addBatchElements(id, map, parameters, insertStmt);
            insertStmt.executeBatch();
        }
    }

    private void addBatchElements(ID id, @NotNull Map<K, List<V>> map, SQLDatabaseParameters parameters, PreparedStatement insertStmt) throws Exception {
        for (Map.Entry<K, List<V>> entry : map.entrySet()) {
            addOneEntry(entry.getValue(), id, entry.getKey(), parameters, insertStmt);
        }
    }

    private void addOneEntry(List<V> entry, ID id, K key, SQLDatabaseParameters parameters, PreparedStatement insertStmt) throws Exception {
        for (V value : entry) {
            addEntry(id, key, value, parameters);
            insertStmt.addBatch();
        }
    }

    // Insert a single key-value pair (key -> List<V>)
    public void insert(ID id, K key, List<V> values) throws Exception {
        String insertQuery = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(insertStmt, resolverRegistry);
            addOneEntry(values, id, key, parameters, insertStmt);
            insertStmt.executeBatch();
        }
    }

    // Delete a key-value pair (key -> List<V>)
    public void delete(final ID id, final K key) throws Exception {
        String query = String.format("DELETE FROM %s WHERE id = ? AND map_key = ?;", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry);
            idResolver.insert(parameters, "id", id);
            keyResolver.insert(parameters, "map_key", key);
            stmt.executeUpdate();
        }
    }

    // Delete all entries for a given ID
    public void delete(final ID id) throws Exception {
        String query = String.format("DELETE FROM %s WHERE id = ?;", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry);
            idResolver.insert(parameters, "id", id);
            stmt.executeUpdate();
        }
    }

    private void addEntry(final ID id, final K key, final V value, final SQLDatabaseParameters insertStmt) {
        idResolver.insert(insertStmt, "id", id);
        keyResolver.insert(insertStmt, "map_key", key);
        valueResolver.insert(insertStmt, "map_value", value);
    }
}
