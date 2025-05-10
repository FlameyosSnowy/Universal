package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.sql.SQLSessionCache;
import io.github.flameyossnowy.universal.sql.internals.ResultCache;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;

import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongFunction;

public class MySQLRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected MySQLRepositoryAdapter(@NotNull final SQLConnectionProvider dataSource, final ResultCache<T, ID> cache, final Class<T> repository, final Class<ID> idClass, SessionCache<ID, T> globalCache, LongFunction<SessionCache<ID, T>> sessionCacheLongFunction) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.MYSQL, globalCache, sessionCacheLongFunction);
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