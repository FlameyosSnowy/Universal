package io.github.flameyossnowy.universal.postgresql;

import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.resolver.TypeRegistration;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;

import io.github.flameyossnowy.universal.sql.internals.query.ParameterizedSql;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;

public class PostgreSQLRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected PostgreSQLRepositoryAdapter(
            @NotNull final SQLConnectionProvider dataSource,
            final DefaultResultCache<ParameterizedSql, T, ID> cache,
            final Class<T> repository,
            final Class<ID> idClass,
            SessionCache<ID, T> globalCache,
            LongFunction<SessionCache<ID, T>> sessionCacheLongFunction,
            CacheWarmer<T, ID> cacheWarmer,
            boolean cacheEnabled,
            int maxSize,
            boolean autoCreate,
            @Nullable TypeRegistration typeRegistration
    ) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.POSTGRESQL, globalCache, sessionCacheLongFunction, cacheWarmer, cacheEnabled, maxSize, autoCreate, typeRegistration);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID> PostgreSQLRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> idClass) {
        return new PostgreSQLRepositoryAdapterBuilder<>(repository, idClass);
    }
}