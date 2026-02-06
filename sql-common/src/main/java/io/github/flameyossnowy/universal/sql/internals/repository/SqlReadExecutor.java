package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SqlReadExecutor<T, ID> {
    private final SQLConnectionProvider dataSource;
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;
    private final QueryParseEngine.SQLType sqlType;
    private final SqlParameterBinder<T, ID> parameterBinder;
    private final SqlResultMapper<T, ID> resultMapper;
    private final SqlCacheManager<T, ID> cacheManager;

    public SqlReadExecutor(
        SQLConnectionProvider dataSource,
        CollectionHandler collectionHandler,
        boolean supportsArrays,
        QueryParseEngine.SQLType sqlType,
        SqlParameterBinder<T, ID> parameterBinder,
        SqlResultMapper<T, ID> resultMapper,
        SqlCacheManager<T, ID> cacheManager
    ) {
        this.dataSource = dataSource;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;
        this.sqlType = sqlType;
        this.parameterBinder = parameterBinder;
        this.resultMapper = resultMapper;
        this.cacheManager = cacheManager;
    }

    public @NotNull List<T> search(String sql, boolean first, @NotNull List<FilterOption> filters) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(sql, connection)) {
            statement.setFetchSize(resultMapper.getFetchSizeOrDefault());

            SQLDatabaseParameters parameters = resultMapper.createParameters(statement, sql, collectionHandler, supportsArrays);
            this.parameterBinder.addFilterToPreparedStatement(filters, parameters, resultMapper.getResolverRegistry(), resultMapper.getRepositoryModel(), sqlType);
            try (ResultSet resultSet = statement.executeQuery()) {
                SQLDatabaseResult databaseResult = resultMapper.createDatabaseResult(resultSet, collectionHandler, supportsArrays);
                return first
                    ? fetchFirst(sql, databaseResult)
                    : fetchAll(sql, resultSet);
            }
        }
    }

    private @NotNull List<T> fetchAll(String query, ResultSet resultSet) throws Exception {
        int fetchSize = resultMapper.getFetchSizeOrDefault();
        return resultMapper.mapResults(query, resultSet, new ArrayList<>(fetchSize), collectionHandler, supportsArrays);
    }

    private @NotNull List<T> fetchFirst(String query, @NotNull SQLDatabaseResult result) throws Exception {
        return cacheManager.insertToCache(query, !result.getResultSet().next() ? List.of() : fetchFirstItem(result));
    }

    private List<T> fetchFirstItem(@NotNull SQLDatabaseResult databaseResult) {
        return resultMapper.fetchFirstItem(databaseResult);
    }
}
