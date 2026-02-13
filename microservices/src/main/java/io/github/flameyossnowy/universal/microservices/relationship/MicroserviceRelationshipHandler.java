package io.github.flameyossnowy.universal.microservices.relationship;

import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

public class MicroserviceRelationshipHandler<T, ID> extends AbstractRelationshipHandler<T, ID> {
    public MicroserviceRelationshipHandler(
        RepositoryModel<T, ID> repositoryInformation,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry
    ) {
        super(repositoryInformation, idClass, resolverRegistry);
    }
}
