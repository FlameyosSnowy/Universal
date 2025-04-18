package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.sql.internals.ResultCache;
import io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class SQLiteRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected SQLiteRepositoryAdapter(@NotNull final SQLConnectionProvider dataSource, final ResultCache<T, ID> cache, final Class<T> repository, final Class<ID> idClass) {
        super(dataSource, cache, repository, idClass, QueryParseEngine.SQLType.SQLITE);
    }

    @Contract("_, _ -> new")
    public static <T, ID> @NotNull SQLiteRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> id) {
        return new SQLiteRepositoryAdapterBuilder<>(repository, id);
    }
}