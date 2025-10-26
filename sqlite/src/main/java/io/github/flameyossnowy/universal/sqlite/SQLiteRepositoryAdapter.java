package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.cache.CacheWarmer;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongFunction;

public class SQLiteRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected SQLiteRepositoryAdapter(
            SQLConnectionProvider dataSource,
            DefaultResultCache<String, T, ID> cache,
            Class<T> repository,
            Class<ID> idClass,
            SessionCache<ID, T> globalCache,
            LongFunction<SessionCache<ID, T>> sessionCacheLongFunction,
            CacheWarmer<T, ID> cacheWarmer
    ) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.SQLITE, globalCache, sessionCacheLongFunction, cacheWarmer);
    }

    /**
     * Constructs a new {@link SQLiteRepositoryAdapterBuilder} to create a
     * {@link SQLiteRepositoryAdapter} instance.
     *
     * @param repository the class of the repository.
     * @param idClass    the class of the primary key.
     * @return a new {@link SQLiteRepositoryAdapterBuilder}.
     */
    @Contract("_, _ -> new")
    public static <T, ID> @NotNull SQLiteRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> idClass) {
        return new SQLiteRepositoryAdapterBuilder<>(repository, idClass);
    }
}