package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.sql.internals.SQLObjectFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.List;

public interface RelationalRepositoryAdapter<T, ID> extends RepositoryAdapter<T, ID, Connection> {
    /**
     * Executes a raw SQL query and discards the result. This method is useful for
     * executing DDL statements such as {@code CREATE TABLE} or {@code ALTER TABLE}.
     *
     * @param query The raw SQL query to execute.
     * @return
     */
    TransactionResult<Boolean> executeRawQuery(String query);

    /**
     * Executes a raw SQL query and returns the result set as a list of objects.
     * The parameters are passed to the query using the {@link java.sql.PreparedStatement} API.
     *
     * @param query The raw SQL query to execute.
     * @param params The parameters to pass to the query.
     * @return The result set as a list of objects.
     */
    List<T> executeQuery(String query, Object... params);

    /**
     * Executes a raw SQL query and returns the result set as a list of objects.
     * The parameters are passed to the query as a list of objects.
     *
     * @param query The raw SQL query to execute.
     * @param params The list of parameters to pass to the query.
     * @return The result set as a list of objects.
     */
    default List<T> executeQuery(String query, @NotNull List<Object> params) {
        return executeQuery(query, params.toArray(new Object[0]));
    }

    /**
     * Executes a raw SQL query and returns the result set as a list of objects.
     * The parameters are passed to the query using the {@link java.sql.PreparedStatement} API.
     *
     * @param query The raw SQL query to execute.
     * @param params The list of parameters to pass to the query.
     * @return The result set as a list of objects.
     */
    List<T> executeQueryWithParams(String query, List<SelectOption> params);

    /**
     * Executes a raw SQL query and returns the result set as a list of objects.
     * The parameters are passed to the query using the
     * {@link java.sql.PreparedStatement} API.
     * <p>
     * If the query is a SELECT query, the repository will fetch the results
     * and return a list of objects. If the query is not a SELECT query, the
     * method will return an empty list.
     * <p>
     * If the query is a SELECT query and the <code>first</code> parameter is
     * true, the repository will fetch the first result and return a list of
     * objects. If the query is not a SELECT query or the <code>first</code>
     * parameter is false, the method will return an empty list.
     *
     * @param query The raw SQL query to execute.
     * @param first If true, the repository will fetch the first result and
     *              return a list of objects. If false, the method will return an
     *              empty list.
     * @param params The list of parameters to pass to the query.
     * @return The result set as a list of objects.
     */
    List<T> executeQueryWithParams(String query, boolean first, List<SelectOption> params);

    /**
     * Retrieves the object factory associated with this repository adapter.
     * <p>
     * This method provides access to the {@link io.github.flameyossnowy.universal.sql.internals.SQLObjectFactory} instance
     * used for creating entity objects from the result sets.
     * <p>
     * The object factory is responsible for instantiating and populating
     * entity objects based on the metadata and mappings defined in the
     * repository.
     *
     * @return the {@link SQLObjectFactory} associated with this adapter.
     */
    @ApiStatus.Internal
    SQLObjectFactory<T, ID> getObjectFactory();
}