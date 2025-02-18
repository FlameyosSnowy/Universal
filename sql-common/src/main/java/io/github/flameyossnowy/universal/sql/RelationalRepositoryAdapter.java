package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;

import java.sql.Connection;

public interface RelationalRepositoryAdapter<T, ID> extends RepositoryAdapter<T, ID, Connection> {
    void executeRawQuery(String query);

    ValueTypeResolverRegistry getValueTypeResolverRegistry();
}
