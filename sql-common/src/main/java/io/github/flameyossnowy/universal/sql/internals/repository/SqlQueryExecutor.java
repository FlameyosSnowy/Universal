package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public class SqlQueryExecutor<T, ID> {
    private final SQLConnectionProvider dataSource;
    private final ExceptionHandler<T, ID, Connection> exceptionHandler;
    private final DefaultResultCache<String, T, ID> cache;
    private final RepositoryModel<T, ID> repositoryModel;
    private final SqlReadExecutor<T, ID> readExecutor;
    private final RepositoryAdapter<T, ID, Connection> adapter;

    public SqlQueryExecutor(SQLConnectionProvider dataSource, ExceptionHandler<T, ID, Connection> exceptionHandler, DefaultResultCache<String, T, ID> cache, RepositoryModel<T, ID> repositoryModel, SqlReadExecutor<T, ID> readExecutor, RepositoryAdapter<T, ID, Connection> adapter) {
        this.dataSource = dataSource;
        this.exceptionHandler = exceptionHandler;
        this.cache = cache;
        this.repositoryModel = repositoryModel;
        this.readExecutor = readExecutor;
        this.adapter = adapter;
    }

    public TransactionResult<Boolean> executeRawQuery(final String query) {
        Logging.info(() -> "Parsed query: " + query);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(query, connection)) {
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryModel, adapter);
        }
    }

    public List<T> executeQuery(String query) {
        List<T> result;
        try {
            return cache != null && (result = cache.fetch(query)) != null ? result : readExecutor.search(query, false, List.of());
        } catch (Exception e) {
            return this.exceptionHandler.handleRead(e, repositoryModel, null, adapter);
        }
    }

    public List<T> executeQueryWithParams(String query, SelectQuery selectQuery, List<FilterOption> params) {
        return executeQueryWithParams(query, selectQuery, false, params);
    }

    public List<T> executeQueryWithParams(String query, SelectQuery selectQuery, boolean first, List<FilterOption> params) {
        List<T> result = cache == null ? null : cache.fetch(query);
        try {
            return result != null ? result : readExecutor.search(query, first, params);
        } catch (Exception e) {
            return this.exceptionHandler.handleRead(e, repositoryModel, selectQuery, adapter);
        }
    }
}
