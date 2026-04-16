package io.github.flameyossnowy.universal.mongodb;

import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

import static io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter.mongoPrimaryKeyName;

public class MongoRelationshipHandler<T, ID> extends AbstractRelationshipHandler<T, ID> {
    protected MongoRelationshipHandler(RepositoryModel<T, ID> repositoryModel, Class<ID> idClass, TypeResolverRegistry resolverRegistry) {
        super(repositoryModel, idClass, resolverRegistry);
    }

    protected MongoRelationshipHandler(RepositoryModel<T, ID> repositoryModel, Class<ID> idClass,
                                       TypeResolverRegistry resolverRegistry, CacheConfiguration cacheConfig) {
        super(repositoryModel, idClass, resolverRegistry, cacheConfig);
    }

    @Override
    public SelectQuery createQuery(Object primaryKeyValue, String name, RepositoryModel<?, ?> model) {
        return Query.select()
            .where(mongoPrimaryKeyName(model.fieldByName(name))).eq(primaryKeyValue)
            .limit(1)
            .build();
    }
}
