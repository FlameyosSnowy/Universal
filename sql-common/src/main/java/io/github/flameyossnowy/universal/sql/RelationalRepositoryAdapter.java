package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.FetchedDataResult;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

public interface RelationalRepositoryAdapter<T, ID> extends RepositoryAdapter<T, ID, Connection> {
    void executeRawQuery(String query);

    ResultSet executeRawQuery(String query, Object... params) throws Exception;

    ResultSet executeRawQueryWithParams(String query, List<SelectOption> params) throws Exception;

    default ResultSet executeRawQuery(String query, @NotNull List<Object> params) throws Exception {
        return executeRawQuery(query, params.toArray(new Object[0]));
    }

    FetchedDataResult<T, ID> executeQuery(String query, Object... params);

    default FetchedDataResult<T, ID> executeQuery(String query, @NotNull List<Object> params) {
        return executeQuery(query, params.toArray(new Object[0]));
    }

    FetchedDataResult<T, ID> executeQueryWithParams(String query, List<SelectOption> params);

    FetchedDataResult<T, ID> executeQueryWithParams(String query, boolean first, List<SelectOption> params);

}