package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.sql.internals.ResultCache;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongFunction;

public class SQLiteRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected SQLiteRepositoryAdapter(SQLConnectionProvider dataSource, ResultCache<T, ID> cache, Class<T> repository, Class<ID> idClass, SessionCache<ID, T> globalCache, LongFunction<SessionCache<ID, T>> sessionCacheLongFunction) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.SQLITE, globalCache, sessionCacheLongFunction);
    }

    @Contract("_, _ -> new")
    public static <T, ID> @NotNull SQLiteRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> id) {
        return new SQLiteRepositoryAdapterBuilder<>(repository, id);
    }
}