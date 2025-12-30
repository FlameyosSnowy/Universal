package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.resolvers.CollectionTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MapTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MultiMapTypeResolver;

import java.sql.ResultSet;
import java.util.*;

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
            TypeResolverRegistry resolverRegistry,
            SQLConnectionProvider connectionProvider) {
        super(repositoryInformation, idClass, resolverRegistry);
        this.connectionProvider = connectionProvider;
    }

    public Collection<Object> handleNormalCollections(ID value, Class<?> rawType, CollectionKind kind) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolve(value, kind);
    }

    public Object[] handleNormalArrays(ID value, Class<?> rawType) {
        CollectionTypeResolver<Object, ID> collectionTypeResolver = (CollectionTypeResolver<Object, ID>)
                SQLCollections.INSTANCE.getResolver(rawType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolveArray(value);
    }

    public Map<Object, Object> handleNormalMap(ID value, Class<?> rawKeyType, Class<?> rawValueType) {
        MapTypeResolver<Object, Object, ID> collectionTypeResolver = (MapTypeResolver<Object, Object, ID>)
                SQLCollections.INSTANCE.getMapResolver( rawKeyType, rawValueType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolve(value);
    }

    public Map<Object, List<Object>> handleMultiMap(ID value, Class<?> rawKeyType, Class<?> rawValueType, CollectionKind kind) {
        MultiMapTypeResolver<Object, Object, ID> collectionTypeResolver = (MultiMapTypeResolver<Object, Object, ID>)
                SQLCollections.INSTANCE.getMultiMapResolver( rawKeyType, rawValueType, idClass, connectionProvider, repositoryInformation, resolverRegistry);
        return collectionTypeResolver.resolve(value, kind);
    }
}
