package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

public class SQLRelationshipHandler<T, ID> extends AbstractRelationshipHandler<T, ID> {
    SQLRelationshipHandler(RepositoryModel<T, ID> repositoryModel, Class<ID> idClass, TypeResolverRegistry resolverRegistry) {
        super(repositoryModel, idClass, resolverRegistry);
    }
}
