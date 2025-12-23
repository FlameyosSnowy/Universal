package io.github.flameyossnowy.universal.api.exceptions.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import java.util.List;

public interface ExceptionHandler<T, ID, C> {
    /**
     * Called when an exception is thrown in a read operation.
     *
     * @param e        the exception that was thrown
     * @param information the information about the repository
     * @param query     the query that was being executed
     * @param adapter   the repository adapter that was being used
     * @return the result of the operation
     */
    List<T> handleRead(Exception e, RepositoryInformation information, SelectQuery query, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Called when an exception is thrown in a read operation.
     *
     * @param e        the exception that was thrown
     * @param information the information about the repository
     * @param query     the query that was being executed
     * @param adapter   the repository adapter that was being used
     * @return the result of the operation
     */
    List<ID> handleReadIds(Exception e, RepositoryInformation information, SelectQuery query, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Handles exceptions thrown during an insert operation.
     *
     * @param e the exception that was thrown
     * @param information the information about the repository
     * @param adapter the repository adapter that was being used
     * @return the result of the operation
     */
    TransactionResult<Boolean> handleInsert(Exception e, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Handles exceptions thrown during a delete operation.
     *
     * @param e the exception that was thrown
     * @param information the information about the repository
     * @param adapter the repository adapter that was being used
     * @return the result of the operation
     */
    TransactionResult<Boolean> handleDelete(Exception e, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Handles exceptions thrown during an update operation.
     *
     * @param e the exception that was thrown
     * @param information the information about the repository
     * @param adapter the repository adapter that was being used
     * @return the result of the operation
     */
    TransactionResult<Boolean> handleUpdate(Exception e, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter);
}
