package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
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
    private final SQLConnectionProvider connectionProvider;
    private final TypeResolverRegistry resolverRegistry;
    private final RepositoryInformation information;

    public MultiMapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                                SQLConnectionProvider connectionProvider,
                                @NotNull RepositoryInformation information,
                                @NotNull TypeResolverRegistry resolverRegistry) {
        this.connectionProvider = connectionProvider;
        this.information = information;
        this.resolverRegistry = resolverRegistry;

        this.tableName = information.getRepositoryName() + "_" + valueType.getSimpleName().toLowerCase() + "_map";
        this.keyResolver = resolverRegistry.resolve(keyType);
        this.valueResolver = resolverRegistry.resolve(valueType);
        this.idResolver = resolverRegistry.resolve(idType);

        if (keyResolver == null || valueResolver == null || idResolver == null)
            throw new IllegalStateException("No resolver found for one of the types");
    }

    public <C extends Collection<V>> Map<K, C> resolve(ID id, CollectionKind kind) {
        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(params, "id", id);

            var rs = stmt.executeQuery();
            Map<K, C> map = new HashMap<>(rs.getFetchSize());

            SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry);
            while (rs.next()) {
                K key = keyResolver.resolve(result, "map_key");
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Map<K, ? extends Collection<V>> map) throws Exception {
        String query = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
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