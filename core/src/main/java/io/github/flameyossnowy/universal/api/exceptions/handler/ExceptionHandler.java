package io.github.flameyossnowy.universal.api.exceptions.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.SelectQuery;

import java.util.List;

/**
 * An exception handler for handling custom exceptions.
 * @param <T> The element
 * @param <ID> the ID
 * @param <C> The connection
 * @author flameyosflow
 * @version 3.0.0
 */
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
    List<T> handleRead(Exception e, RepositoryModel<T, ID> information, SelectQuery query, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Called when an exception is thrown in a read operation.
     *
     * @param e        the exception that was thrown
     * @param information the information about the repository
     * @param query     the query that was being executed
     * @param adapter   the repository adapter that was being used
     * @return the result of the operation
     */
    List<ID> handleReadIds(Exception e, RepositoryModel<T, ID> information, SelectQuery query, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Handles exceptions thrown during an insert operation.
     *
     * @param e the exception that was thrown
     * @param information the information about the repository
     * @param adapter the repository adapter that was being used
     * @return the result of the operation
     */
    TransactionResult<Boolean> handleInsert(Exception e, RepositoryModel<T, ID> information, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Handles exceptions thrown during a delete operation.
     *
     * @param e the exception that was thrown
     * @param information the information about the repository
     * @param adapter the repository adapter that was being used
     * @return the result of the operation
     */
    TransactionResult<Boolean> handleDelete(Exception e, RepositoryModel<T, ID> information, RepositoryAdapter<T, ID, C> adapter);

    /**
     * Handles exceptions thrown during an update operation.
     *
     * @param e the exception that was thrown
     * @param information the information about the repository
     * @param adapter the repository adapter that was being used
     * @return the result of the operation
     */
    TransactionResult<Boolean> handleUpdate(Exception e, RepositoryModel<T, ID> information, RepositoryAdapter<T, ID, C> adapter);
}
