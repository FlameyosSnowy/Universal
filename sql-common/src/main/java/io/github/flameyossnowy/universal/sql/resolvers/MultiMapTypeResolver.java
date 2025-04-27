package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

@SuppressWarnings("unused")
public class MultiMapTypeResolver<K, V, ID> {
    private final String tableName;

    private final SQLValueTypeResolver<K> keyResolver;
    private final SQLValueTypeResolver<V> valueResolver;
    private final SQLValueTypeResolver<ID> idResolver;

    private final SQLConnectionProvider connectionProvider;
    private final RepositoryInformation information;

    public MultiMapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                                 final SQLConnectionProvider connectionProvider,
                                 final @NotNull RepositoryInformation information) {
        this.connectionProvider = connectionProvider;
        this.information = information;

        this.tableName = information.getRepositoryName() + "_" + valueType.getSimpleName().toLowerCase() + "_map";
        this.keyResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(keyType);
        this.valueResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(valueType);
        this.idResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idType);

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
                ValueTypeResolverRegistry.INSTANCE.getType(idResolver),
                ValueTypeResolverRegistry.INSTANCE.getType(keyResolver),
                ValueTypeResolverRegistry.INSTANCE.getType(valueResolver), information.getRepositoryName());

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, conn)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create map table: " + tableName, e);
        }
    }

    // Support for List<V> as the value type in the Map
    public Map<K, List<V>> resolve(ID id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE id = ?;")) {
            idResolver.insert(stmt, 1, id);
            ResultSet resultSet = stmt.executeQuery();

            Map<K, List<V>> map = new HashMap<>();
            while (resultSet.next()) {
                K key = keyResolver.resolve(resultSet, "map_key");
                V value = valueResolver.resolve(resultSet, "map_value");

                map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Insert List<V> into the map
    public void insert(ID id, @NotNull Map<K, List<V>> map) throws Exception {
        String insertQuery = String.format("INSERT INTO %s (id, map_key, map_value) VALUES (?, ?, ?)", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            for (Map.Entry<K, List<V>> entry : map.entrySet()) {
                for (V value : entry.getValue()) {
                    addEntry(id, entry.getKey(), value, insertStmt);
                    insertStmt.addBatch();
                }
            }
            insertStmt.executeBatch();
        }
    }

    // Insert a single key-value pair (key -> List<V>)
    public void insert(ID id, K key, List<V> values) throws Exception {
        String insertQuery = String.format("INSERT INTO %s (id, map_key, map_value) VALUES (?, ?, ?)", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            for (V value : values) {
                addEntry(id, key, value, insertStmt);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        }
    }

    // Delete key-value pair (key -> List<V>)
    public void delete(final ID id, final K key) throws Exception {
        String query = String.format("DELETE FROM %s WHERE id = ? AND map_key = ?;", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            idResolver.insert(stmt, 1, id);
            keyResolver.insert(stmt, 2, key);
            stmt.executeUpdate();
        }
    }

    // Delete all entries for a given ID
    public void delete(final ID id) throws Exception {
        String query = String.format("DELETE FROM %s WHERE id = ?;", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            idResolver.insert(stmt, 1, id);
            stmt.executeUpdate();
        }
    }

    private void addEntry(final ID id, final K key, final V value, final PreparedStatement insertStmt) throws Exception {
        idResolver.insert(insertStmt, 1, id);
        keyResolver.insert(insertStmt, 2, key);
        valueResolver.insert(insertStmt, 3, value);
    }
}
