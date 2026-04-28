package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.internals.query.ParameterizedSql;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;

@SuppressWarnings("unused")
public class MultiMapTypeResolver<K, V, ID> {
    private final TypeResolver<K> keyResolver;
    private final TypeResolver<V> valueResolver;
    private final TypeResolver<ID> idResolver;
    private final SQLConnectionProvider connectionProvider;
    private final TypeResolverRegistry resolverRegistry;
    private final RepositoryModel<?, ID> information;
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;

    private final ParameterizedSql selectSql;
    private final ParameterizedSql insertSql;

    public MultiMapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                                String fieldName,
                                SQLConnectionProvider connectionProvider,
                                @NotNull RepositoryModel<?, ID> information,
                                @NotNull TypeResolverRegistry resolverRegistry,
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
            throw new IllegalStateException("No resolver found for one of the types");
        }

        // Table name includes field name to ensure uniqueness per field
        String table = information.tableName() + "_" + fieldName.toLowerCase() + "_multimap";

        // Create table if not exists (similar to CollectionTypeResolver)
        createTableIfNotExists(table, keyType, valueType, idType);

        this.selectSql = ParameterizedSql.of("SELECT * FROM " + table + " WHERE id = ?;",                          List.of("id"));
        this.insertSql = ParameterizedSql.of("INSERT INTO " + table + " (id, map_key, map_value) VALUES (?, ?, ?)", List.of("id", "map_key", "map_value"));
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
                "FOREIGN KEY (id) REFERENCES " + information.tableName() + "(id) ON DELETE CASCADE ON UPDATE CASCADE" +
                ")";

            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create multimap table: " + table, e);
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

    @SuppressWarnings("unchecked")
    public <C extends Collection<V>> Map<K, C> resolve(ID id, CollectionKind kind) {
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(selectSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, selectSql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);

            try (var rs = stmt.executeQuery()) {
                Map<K, C> map = new HashMap<>(rs.getFetchSize());
                SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry, collectionHandler, supportsArrays, information);

                while (rs.next()) {
                    K key   = keyResolver.resolve(result, "map_key");
                    V value = valueResolver.resolve(result, "map_value");
                    map.computeIfAbsent(key, k -> {
                        try {
                            return (C) kind.create(rs.getFetchSize());
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }).add(value);
                }
                return map;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Map<K, ? extends Collection<V>> map) throws Exception {
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(insertSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, insertSql, information, collectionHandler, supportsArrays);
            for (var entry : map.entrySet()) {
                K key = entry.getKey();
                for (V value : entry.getValue()) {
                    idResolver.insert(params, "id", id);
                    keyResolver.insert(params, "map_key", key);
                    valueResolver.insert(params, "map_value", value);
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }
    }
}