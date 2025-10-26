package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
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
    private final Table<Class<?>, Class<?>, MultiMapTypeResolver<?, ?, ?>> multiMapResolvers = new HashTable<>();

    public static final SQLCollections INSTANCE = new SQLCollections();

    @SuppressWarnings("unchecked")
    public <T, ID> CollectionTypeResolver<T, ID> getResolver(
            Class<T> elementType, Class<ID> idType, SQLConnectionProvider connectionProvider,
            RepositoryInformation information, TypeResolverRegistry resolverRegistry
    ) {
        return (CollectionTypeResolver<T, ID>)
                resolvers.computeIfAbsent(elementType, k -> new CollectionTypeResolver<>(idType, elementType, connectionProvider, information, resolverRegistry));
    }

    @SuppressWarnings("unchecked")
    public <K, V, ID> MapTypeResolver<K, V, ID> getMapResolver(
            Class<K> keyType, Class<V> valueType, Class<ID> idType,
            SQLConnectionProvider connectionProvider, RepositoryInformation information, TypeResolverRegistry resolverRegistry
    ) {
        return (MapTypeResolver<K, V, ID>) mapResolvers.computeIfAbsent(keyType, valueType,
                (k, v) -> new MapTypeResolver<>(idType, keyType, valueType, connectionProvider, information, resolverRegistry));
    }

    @SuppressWarnings("unchecked")
    public <K, V, ID> MultiMapTypeResolver<K, V, ID> getMultiMapResolver(
            Class<K> keyType, Class<V> valueType, Class<ID> idType,
            SQLConnectionProvider connectionProvider, RepositoryInformation information, TypeResolverRegistry resolverRegistry
    ) {
        return (MultiMapTypeResolver<K, V, ID>) multiMapResolvers.computeIfAbsent(keyType, valueType,
                (k, v) -> new MultiMapTypeResolver<>(idType, keyType, valueType, connectionProvider, information, resolverRegistry));
    }
}
