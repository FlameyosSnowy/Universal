package io.github.flameyossnowy.universal.mongodb;

import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

public class MongoRelationshipHandler<T, ID> extends AbstractRelationshipHandler<T, ID> {
    protected MongoRelationshipHandler(RepositoryModel<T, ID> repositoryModel, Class<ID> idClass, TypeResolverRegistry resolverRegistry) {
        super(repositoryModel, idClass, resolverRegistry);
    }
}
