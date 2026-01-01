package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;

import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongFunction;

public class MySQLRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected MySQLRepositoryAdapter(
            @NotNull final SQLConnectionProvider dataSource,
            final DefaultResultCache<String, T, ID> cache,
            final Class<T> repository,
            final Class<ID> idClass,
            SessionCache<ID, T> globalCache,
            LongFunction<SessionCache<ID, T>> sessionCacheLongFunction,
            CacheWarmer<T, ID> cacheWarmer,
            boolean cacheEnabled,
            int maxSize
    ) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.MYSQL, globalCache, sessionCacheLongFunction, cacheWarmer, cacheEnabled, maxSize);
    }

    /**
     * Constructs a new {@link MySQLRepositoryAdapterBuilder} to create a
     * {@link MySQLRepositoryAdapter} instance.
     *
     * @param repository the class of the repository.
     * @param idClass    the class of the primary key.
     * @return a new {@link MySQLRepositoryAdapterBuilder}.
     */
    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID> MySQLRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> idClass) {
        return new MySQLRepositoryAdapterBuilder<>(repository, idClass);
    }
}