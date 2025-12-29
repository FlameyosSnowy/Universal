package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class MapTypeResolver<K, V, ID> {
    private final String tableName;

    private final TypeResolver<K> keyResolver;
    private final TypeResolver<V> valueResolver;
    private final TypeResolver<ID> idResolver;

    private final SQLConnectionProvider connectionProvider;
    private final RepositoryInformation information;

    @NotNull
    private final TypeResolverRegistry resolverRegistry;

    public MapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                           final SQLConnectionProvider connectionProvider,
                           final @NotNull RepositoryInformation information, TypeResolverRegistry resolverRegistry) {
        this.connectionProvider = connectionProvider;
        this.information = information;
        this.resolverRegistry = resolverRegistry;

        this.tableName = information.getRepositoryName() + "_" + valueType.getSimpleName().toLowerCase() + "_map";
        this.keyResolver = resolverRegistry.resolve(keyType);
        this.valueResolver = resolverRegistry.resolve(valueType);
        this.idResolver = resolverRegistry.resolve(idType);

        if (keyResolver == null || valueResolver == null || idResolver == null) {
            throw new IllegalStateException("No resolver found for one of the types: " + keyType.getSimpleName() + ", " + valueType.getSimpleName() + ", or " + idType.getSimpleName());
        }
    }

    public Map<K, V> resolve(ID id) {
        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(parameters, "id", id);
            ResultSet resultSet = stmt.executeQuery();
            if (!resultSet.next()) return Map.of();
            SQLDatabaseResult result = new SQLDatabaseResult(resultSet, resolverRegistry);

            Map<K, V> map = new HashMap<>(stmt.getFetchSize());
            while (resultSet.next()) {
                K key = keyResolver.resolve(result, "map_key");
                V value = valueResolver.resolve(result, "map_value");
                map.put(key, value);
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Map<K, V> map) throws Exception {
        String insertQuery = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            for (Map.Entry<K, V> entry : map.entrySet()) {
                SQLDatabaseParameters parameters = new SQLDatabaseParameters(insertStmt, resolverRegistry, insertQuery, information);
                addEntry(id, entry.getKey(), entry.getValue(), insertStmt, parameters);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        }
    }

    public void insert(ID id, K key, V value) throws Exception {
        String insertQuery = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(insertStmt, resolverRegistry, insertQuery, information);
            addEntry(id, key, value, insertStmt, parameters);
            insertStmt.executeUpdate();
        }
    }

    public void delete(final ID id, final K key) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ? AND map_key = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(parameters, "id", id);
            keyResolver.insert(parameters, "map_key", key);
            stmt.executeUpdate();
        }
    }

    public void delete(final ID id) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(parameters, "id", id);
            stmt.executeUpdate();
        }
    }

    private void addEntry(final ID id, final K key, final V value, final PreparedStatement insertStmt, SQLDatabaseParameters parameters) throws Exception {
        idResolver.insert(parameters, "id", id);
        keyResolver.insert(parameters, "map_key", key);
        valueResolver.insert(parameters, "map_value", value);
    }
}
