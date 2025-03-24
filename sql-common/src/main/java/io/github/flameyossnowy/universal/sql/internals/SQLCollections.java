package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.sql.resolvers.NormalCollectionTypeResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SQLCollections {
    private final Map<Class<?>, NormalCollectionTypeResolver<?, ?>> resolvers = new ConcurrentHashMap<>();

    public static final SQLCollections INSTANCE = new SQLCollections();

    @SuppressWarnings("unchecked")
    public <T, ID> NormalCollectionTypeResolver<T, ID> getResolver(Class<T> elementType, Class<ID> idType, SQLConnectionProvider connectionProvider, RepositoryInformation information) {
        return (NormalCollectionTypeResolver<T, ID>) resolvers.computeIfAbsent(elementType, k -> new NormalCollectionTypeResolver<>(idType, elementType, connectionProvider, information));
    }
}
