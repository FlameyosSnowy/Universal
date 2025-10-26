package io.github.flameyossnow.universal.cassandra.collections;

import com.datastax.driver.core.*;
import io.github.flameyossnow.universal.cassandra.objects.CassandraDatabaseParameters;
import io.github.flameyossnow.universal.cassandra.objects.CassandraDatabaseResult;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cassandra equivalent of MultiMapTypeResolver for SQL.
 */
@SuppressWarnings("unused")
public class MultiMapTypeResolver<K, V, ID> {
    private final String tableName;

    private final TypeResolver<K> keyResolver;
    private final TypeResolver<V> valueResolver;
    private final TypeResolver<ID> idResolver;

    private final TypeResolverRegistry resolverRegistry;
    private final Session session;
    
    // PreparedStatement cache for performance
    private final Map<String, PreparedStatement> preparedStatementCache = new ConcurrentHashMap<>();

    public MultiMapTypeResolver(Class<ID> idType, Class<K> keyType, @NotNull Class<V> valueType,
                                final Session session,
                                final @NotNull RepositoryInformation information,
                                final @NotNull TypeResolverRegistry resolverRegistry) {
        this.session = session;
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
        String idType = resolverRegistry.getType(idResolver);
        String keyType = resolverRegistry.getType(keyResolver);
        String valueType = resolverRegistry.getType(valueResolver);

        // No FK constraints in Cassandra, simple PK on (id, map_key)
        String query = String.format(
            """
            CREATE TABLE IF NOT EXISTS %s (
                id %s,
                map_key %s,
                map_value %s,
                PRIMARY KEY (id, map_key, map_value)
            );
            """,
            tableName, idType, keyType, valueType
        );

        session.execute(query);
    }

    public Map<K, List<V>> resolve(ID id) {
        String query = "SELECT map_key, map_value FROM " + tableName + " WHERE id = ?;";
        CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
        PreparedStatement prepared = getPreparedStatement(query);

        idResolver.insert(parameters, "id", id);

        BoundStatement bound = prepared.bind(parameters.getValues());
        ResultSet resultSet = session.execute(bound);

        Map<K, List<V>> map = new HashMap<>();
        for (Row row : resultSet) {
            CassandraDatabaseResult result = new CassandraDatabaseResult(row, resolverRegistry);
            K key = keyResolver.resolve(result, "map_key");
            V value = valueResolver.resolve(result, "map_value");

            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    public void insert(ID id, @NotNull Map<K, List<V>> map) {
        // Use UNLOGGED for better performance when writing to same partition
        BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
        String insertQuery = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        PreparedStatement prepared = getPreparedStatement(insertQuery);

        for (Map.Entry<K, List<V>> entry : map.entrySet()) {
            addBatchElements(batch, id, entry.getKey(), entry.getValue(), prepared);
        }

        session.execute(batch);
    }

    private void addBatchElements(BatchStatement batch, ID id, K key, List<V> values, PreparedStatement prepared) {
        for (V value : values) {
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
            idResolver.insert(parameters, "id", id);
            keyResolver.insert(parameters, "map_key", key);
            valueResolver.insert(parameters, "map_value", value);
            BoundStatement bound = prepared.bind(parameters.getValues());
            batch.add(bound);
        }
    }

    public void insert(ID id, K key, List<V> values) {
        // Use UNLOGGED for better performance when writing to same partition
        BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);
        String insertQuery = "INSERT INTO " + tableName + " (id, map_key, map_value) VALUES (?, ?, ?)";
        PreparedStatement prepared = getPreparedStatement(insertQuery);

        addBatchElements(batch, id, key, values, prepared);

        session.execute(batch);
    }

    public void delete(ID id, K key) {
        String query = String.format("DELETE FROM %s WHERE id = ? AND map_key = ?;", tableName);
        PreparedStatement prepared = getPreparedStatement(query);
        CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
        idResolver.insert(parameters, "id", id);
        keyResolver.insert(parameters, "map_key", key);
        BoundStatement bound = prepared.bind(parameters.getValues());
        session.execute(bound);
    }
    
    /**
     * Gets a cached PreparedStatement or prepares and caches a new one.
     */
    private PreparedStatement getPreparedStatement(String cql) {
        return preparedStatementCache.computeIfAbsent(cql, session::prepare);
    }

    public void delete(ID id) {
        String query = String.format("DELETE FROM %s WHERE id = ?;", tableName);
        PreparedStatement prepared = getPreparedStatement(query);
        CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();

        idResolver.insert(parameters, "id", id);
        BoundStatement bound = prepared.bind(parameters.getValues());
        session.execute(bound);
    }
}
