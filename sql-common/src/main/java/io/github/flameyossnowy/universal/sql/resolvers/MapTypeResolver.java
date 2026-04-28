package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.internals.query.ParameterizedSql;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class MapTypeResolver<K, V, ID> {
    private final TypeResolver<K> keyResolver;
    private final TypeResolver<V> valueResolver;
    private final TypeResolver<ID> idResolver;
    private final SQLConnectionProvider connectionProvider;
    private final RepositoryModel<?, ID> information;
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;

    @NotNull
    private final TypeResolverRegistry resolverRegistry;

    private final ParameterizedSql selectSql;
    private final ParameterizedSql insertSql;
    private final ParameterizedSql deleteKeySql;
    private final ParameterizedSql deleteAllSql;

    public MapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                           final SQLConnectionProvider connectionProvider,
                           final @NotNull RepositoryModel<?, ID> information,
                           TypeResolverRegistry resolverRegistry,
                           CollectionHandler collectionHandler, boolean supportsArrays) {
        this.connectionProvider = connectionProvider;
        this.information = information;
        this.resolverRegistry = resolverRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;

        this.keyResolver   = resolverRegistry.resolve(keyType);
        this.valueResolver = resolverRegistry.resolve(valueType);
        this.idResolver    = resolverRegistry.resolve(idType);

        if (keyResolver == null || valueResolver == null || idResolver == null) {
            throw new IllegalStateException("No resolver found for one of the types: "
                + keyType.getSimpleName() + ", " + valueType.getSimpleName() + ", or " + idType.getSimpleName());
        }

        String table = information.tableName() + "_" + valueType.getSimpleName().toLowerCase() + "s";

        createTableIfNotExists(table, keyType, valueType, idType);

        this.selectSql    = ParameterizedSql.of("SELECT * FROM " + table + " WHERE id = ?;",                          List.of("id"));
        this.insertSql    = ParameterizedSql.of("INSERT INTO " + table + " (id, map_key, map_value) VALUES (?, ?, ?)", List.of("id", "map_key", "map_value"));
        this.deleteKeySql = ParameterizedSql.of("DELETE FROM " + table + " WHERE id = ? AND map_key = ?;",            List.of("id", "map_key"));
        this.deleteAllSql = ParameterizedSql.of("DELETE FROM " + table + " WHERE id = ?;",                            List.of("id"));
    }

    private void createTableIfNotExists(String table, Class<K> keyType, Class<V> valueType, Class<ID> idType) {
        try (Connection connection = connectionProvider.getConnection();
             Statement stmt = connection.createStatement()) {

            String idSqlType = getSqlType(idType);
            String keySqlType = getSqlType(keyType);
            String valueSqlType = getSqlType(valueType);

            String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "id " + idSqlType + " NOT NULL, " +
                "map_key " + keySqlType + " NOT NULL, " +
                "map_value " + valueSqlType + " NOT NULL, " +
                "PRIMARY KEY (id, map_key), " +
                "FOREIGN KEY (id) REFERENCES " + information.tableName() + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ")";

            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create map table: " + table, e);
        }
    }

    private String getSqlType(Class<?> type) {
        try {
            String sqlType = resolverRegistry.getType(type);
            return sqlType != null ? sqlType : "TEXT";
        } catch (IllegalArgumentException e) {
            // Type not resolvable (e.g., Map.class, List.class) - use TEXT for JSON
            return "TEXT";
        }
    }

    public Map<K, V> resolve(ID id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(selectSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, selectSql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);

            try (ResultSet rs = stmt.executeQuery()) {
                SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry, collectionHandler, supportsArrays, information);
                Map<K, V> map = new HashMap<>(Math.max(stmt.getFetchSize(), 32));

                while (rs.next()) {
                    K key   = keyResolver.resolve(result, "map_key");
                    V value = valueResolver.resolve(result, "map_value");
                    map.put(key, value);
                }

                return map;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Map<K, V> map) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(insertSql.sql(), connection)) {

            for (Map.Entry<K, V> entry : map.entrySet()) {
                SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, insertSql, information, collectionHandler, supportsArrays);
                addEntry(id, entry.getKey(), entry.getValue(), params);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public void insert(ID id, K key, V value) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(insertSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, insertSql, information, collectionHandler, supportsArrays);
            addEntry(id, key, value, params);
            stmt.executeUpdate();
        }
    }

    public void delete(final ID id, final K key) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(deleteKeySql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, deleteKeySql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);
            keyResolver.insert(params, "map_key", key);
            stmt.executeUpdate();
        }
    }

    public void delete(final ID id) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(deleteAllSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, deleteAllSql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);
            stmt.executeUpdate();
        }
    }

    private void addEntry(final ID id, final K key, final V value, SQLDatabaseParameters params) {
        idResolver.insert(params, "id", id);
        keyResolver.insert(params, "map_key", key);
        valueResolver.insert(params, "map_value", value);
    }
}