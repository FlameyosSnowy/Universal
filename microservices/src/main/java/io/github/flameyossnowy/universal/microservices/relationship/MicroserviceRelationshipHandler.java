package io.github.flameyossnowy.universal.microservices.relationship;

import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

public class MicroserviceRelationshipHandler<T, ID, C> extends AbstractRelationshipHandler<T, ID, C> {
    public MicroserviceRelationshipHandler(
        RepositoryInformation repositoryInformation,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry
    ) {
        super(repositoryInformation, idClass, resolverRegistry);
    }
}
