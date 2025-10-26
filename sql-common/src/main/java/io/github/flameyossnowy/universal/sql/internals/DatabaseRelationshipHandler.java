package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.resolvers.CollectionTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MapTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MultiMapTypeResolver;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fully portable, backend-agnostic relationship handler.
 * Takes in a repository map instead of static adapter access.
 */
@SuppressWarnings("unchecked")
public class DatabaseRelationshipHandler<T, ID> extends AbstractRelationshipHandler<T, ID, ResultSet> {
    private final SQLConnectionProvider connectionProvider;

    public DatabaseRelationshipHandler(
            RepositoryInformation repositoryInformation,
            Class<ID> idClass,
            Map<Class<?>, RepositoryAdapter<?, ?, ?>> repositories,
            TypeResolverRegistry resolverRegistry,
            SQLConnectionProvider connectionProvider) {
        super(repositoryInformation, idClass, repositories, resolverRegistry);
        this.connectionProvider = connectionProvider;
    }

    public Collection<Object> handleNormalLists(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolve(value);
    }

    public Object[] handleNormalArrays(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolveArray(value);
    }

    public Collection<Object> handleNormalSets(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolveSet(value);
    }

    public Map<Object, Object> handleNormalMap(ID value, Class<?> rawKeyType, Class<?> rawValueType) {
        MapTypeResolver<Object, Object, ID> collectionTypeResolver = (MapTypeResolver<Object, Object, ID>)
                SQLCollections.INSTANCE.getMapResolver( rawKeyType, rawValueType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolve(value);
    }

    public Map<Object, List<Object>> handleMultiMap(ID value, Class<?> rawKeyType, Class<?> rawValueType) {
        MultiMapTypeResolver<Object, Object, ID> collectionTypeResolver = (MultiMapTypeResolver<Object, Object, ID>)
                SQLCollections.INSTANCE.getMultiMapResolver( rawKeyType, rawValueType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolve(value);
    }
}
