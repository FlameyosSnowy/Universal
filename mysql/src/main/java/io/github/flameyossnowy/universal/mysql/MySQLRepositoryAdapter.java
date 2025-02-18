package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.cache.ResultCache;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.sql.AbstractRelationalRepositoryAdapter;

import io.github.flameyossnowy.universal.sql.QueryParseEngine;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.*;

public class MySQLRepositoryAdapter<T, ID> extends AbstractRelationalRepositoryAdapter<T, ID> {
    protected MySQLRepositoryAdapter(@NotNull final ConnectionProvider<Connection> dataSource, final ResultCache cache, final EnumSet<Optimizations> optimizations, final Class<T> repository) {
        super(dataSource, cache, optimizations, repository, QueryParseEngine.SQLType.SQLITE);
    }

    @NotNull
    @Contract("_, _ -> new")
    public static <T, ID> MySQLRepositoryAdapterBuilder<T, ID> builder(Class<T> repository, Class<ID> ignored) {
        return new MySQLRepositoryAdapterBuilder<>(repository);
    }
}