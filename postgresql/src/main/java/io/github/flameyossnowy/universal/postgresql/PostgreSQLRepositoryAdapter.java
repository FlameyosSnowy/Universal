package io.github.flameyossnowy.universal.postgresql;

import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.postgresql.internals.PostgresObjectFactory;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.ResultCache;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.UUID;
import java.util.function.LongFunction;

public class PostgreSQLRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    static {
        ValueTypeResolverRegistry.INSTANCE.registerEncodedTypeMapper(InetAddress.class, "INET");
        ValueTypeResolverRegistry.INSTANCE.registerEncodedTypeMapper(UUID.class, "UUID");
        ValueTypeResolverRegistry.INSTANCE.registerEncodedTypeMapper(String.class, "TEXT");
    }

    protected PostgreSQLRepositoryAdapter(@NotNull final SQLConnectionProvider dataSource, final ResultCache<T, ID> cache, final Class<T> repository, final Class<ID> idClass, SessionCache<ID, T> globalCache, LongFunction<SessionCache<ID, T>> sessionCacheLongFunction) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.POSTGRESQL, globalCache, sessionCacheLongFunction);
        this.setObjectFactory(new PostgresObjectFactory<>(repositoryInformation, dataSource, this));
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID> PostgreSQLRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> idClass) {
        return new PostgreSQLRepositoryAdapterBuilder<>(repository, idClass);
    }
}