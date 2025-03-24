package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.cache.ResultCache;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;

import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class MySQLRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected MySQLRepositoryAdapter(@NotNull final SQLConnectionProvider dataSource, final ResultCache<T, ID> cache, final Class<T> repository, final Class<ID> idClass) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.MYSQL);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID> MySQLRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> idClass) {
        return new MySQLRepositoryAdapterBuilder<>(repository, idClass);
    }
}