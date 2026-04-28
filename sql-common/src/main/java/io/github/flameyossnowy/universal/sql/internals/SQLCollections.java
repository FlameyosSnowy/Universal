package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.resolvers.MultiMapTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.CollectionTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MapTypeResolver;
import io.github.flameyossnowy.velocis.tables.HashTable;
import io.github.flameyossnowy.velocis.tables.Table;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SQLCollections {
    private final Map<Class<?>, CollectionTypeResolver<?, ?>> resolvers = new ConcurrentHashMap<>(5);
    private final Table<Class<?>, Class<?>, MapTypeResolver<?, ?, ?>> mapResolvers = new HashTable<>();
    // Key for multimap: fieldName (includes table name + field, ensures uniqueness)
    private final Map<String, MultiMapTypeResolver<?, ?, ?>> multiMapResolvers = new ConcurrentHashMap<>(5);

    public static final SQLCollections INSTANCE = new SQLCollections();

    @SuppressWarnings("unchecked")
    public <T, ID> CollectionTypeResolver<T, ID> getResolver(
        Class<T> elementType, Class<ID> idType, SQLConnectionProvider connectionProvider,
        RepositoryModel<?, ID> information, TypeResolverRegistry resolverRegistry,
        CollectionHandler collectionHandler, boolean supportsArrays
    ) {
        return (CollectionTypeResolver<T, ID>)
                resolvers.computeIfAbsent(elementType, k ->
                    new CollectionTypeResolver<>(idType, elementType, connectionProvider, information, resolverRegistry, collectionHandler, supportsArrays));
    }

    @SuppressWarnings("unchecked")
    public <K, V, ID> MapTypeResolver<K, V, ID> getMapResolver(
            Class<K> keyType, Class<V> valueType, Class<ID> idType,
            SQLConnectionProvider connectionProvider, RepositoryModel<?, ID> information,
            TypeResolverRegistry resolverRegistry, CollectionHandler collectionHandler, boolean supportsArrays
    ) {
        return (MapTypeResolver<K, V, ID>) mapResolvers.computeIfAbsent(keyType, valueType,
                (k, v) ->
                    new MapTypeResolver<>(idType, keyType, valueType, connectionProvider, information, resolverRegistry, collectionHandler, supportsArrays));
    }

    @SuppressWarnings("unchecked")
    public <K, V, ID> MultiMapTypeResolver<K, V, ID> getMultiMapResolver(
            Class<K> keyType, Class<V> valueType, Class<ID> idType,
            String fieldName,
            SQLConnectionProvider connectionProvider, RepositoryModel<?, ID> information,
            TypeResolverRegistry resolverRegistry, CollectionHandler collectionHandler, boolean supportsArrays
    ) {
        // Use fieldName (tableName_fieldName) as key to ensure unique resolver per field
        String cacheKey = information.tableName() + "." + fieldName;
        return (MultiMapTypeResolver<K, V, ID>) multiMapResolvers.computeIfAbsent(cacheKey, k ->
                    new MultiMapTypeResolver<>(idType, keyType, valueType, fieldName, connectionProvider, information, resolverRegistry, collectionHandler, supportsArrays));
    }
}
