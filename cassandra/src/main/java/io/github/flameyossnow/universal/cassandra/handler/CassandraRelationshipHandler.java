package io.github.flameyossnow.universal.cassandra.handler;

import com.datastax.driver.core.Row;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;

import java.util.Map;

@SuppressWarnings("unchecked")
public class CassandraRelationshipHandler<T, ID> extends AbstractRelationshipHandler<T, ID, Row> {
    public CassandraRelationshipHandler(
            RepositoryInformation repositoryInformation,
            Class<ID> idClass,
            Map<Class<?>, RepositoryAdapter<?, ?, ?>> repositories,
            TypeResolverRegistry resolverRegistry
    ) {
        super(repositoryInformation, idClass, repositories, resolverRegistry);
    }
}
