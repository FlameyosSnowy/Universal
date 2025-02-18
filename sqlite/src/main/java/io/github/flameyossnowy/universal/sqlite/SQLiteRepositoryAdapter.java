package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.cache.ResultCache;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.sql.AbstractRelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.QueryParseEngine;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.EnumSet;

public class SQLiteRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected SQLiteRepositoryAdapter(@NotNull final ConnectionProvider<Connection> dataSource, final ResultCache cache, final EnumSet<Optimizations> optimizations, final Class<T> repository) {
        super(dataSource, cache, optimizations, repository, QueryParseEngine.SQLType.SQLITE);
    }

    public static <T, ID> SQLiteRepositoryAdapterBuilder<T, ID> builder(Class<T> repository) {
        return new SQLiteRepositoryAdapterBuilder<>(repository);
    }
}