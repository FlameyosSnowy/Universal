package io.github.flameyossnow.universal.cassandra.factory;

import com.datastax.driver.core.Session;
import io.github.flameyossnow.universal.cassandra.collections.MultiMapTypeResolver;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.velocis.tables.HashTable;
import io.github.flameyossnowy.velocis.tables.Table;

public class CassandraCollections {
    private final Table<Class<?>, Class<?>, MultiMapTypeResolver<?, ?, ?>> multiMapResolvers = new HashTable<>();

    public static final CassandraCollections INSTANCE = new CassandraCollections();

    @SuppressWarnings("unchecked")
    public <K, V, ID> MultiMapTypeResolver<K, V, ID> getMultiMapResolver(
            Class<K> keyType, Class<V> valueType, Class<ID> idType,
            Session session, RepositoryInformation information, TypeResolverRegistry resolverRegistry
    ) {
        return (MultiMapTypeResolver<K, V, ID>) multiMapResolvers.computeIfAbsent(keyType, valueType,
                (k, v) -> new MultiMapTypeResolver<>(idType, keyType, valueType, session, information, resolverRegistry));
    }
}
