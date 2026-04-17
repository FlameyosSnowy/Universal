package io.github.flameyossnowy.universal.api.exceptions.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.exceptions.DeleteRepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.InsertRepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.UpdateRepositoryException;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DefaultExceptionHandler<T, ID, C> implements ExceptionHandler<T, ID, C> {

    @Override
    public TransactionResult<Boolean> handleInsert(
        @NotNull Exception exception,
        @NotNull RepositoryModel<T, ID> information,
        @NotNull RepositoryAdapter<T, ID, C> adapter
    ) {
        String message = createExceptionMessage(exception, information, adapter, "Insert");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return TransactionResult.failure(new InsertRepositoryException(message, exception));
    }

    @Override
    public TransactionResult<Boolean> handleDelete(
        Exception exception,
        RepositoryModel<T, ID> information,
        RepositoryAdapter<T, ID, C> adapter
    ) {
        String message = createExceptionMessage(exception, information, adapter, "Delete");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return TransactionResult.failure(new DeleteRepositoryException(message, exception));
    }

    @Override
    public TransactionResult<Boolean> handleUpdate(
        Exception exception,
        RepositoryModel<T, ID> information,
        RepositoryAdapter<T, ID, C> adapter
    ) {
        String message = createExceptionMessage(exception, information, adapter, "Update");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return TransactionResult.failure(new UpdateRepositoryException(message, exception));
    }

    @Override
    public List<T> handleRead(
        @NotNull Exception exception,
        @NotNull RepositoryModel<T, ID> information,
        SelectQuery query,
        @NotNull RepositoryAdapter<T, ID, C> adapter
    ) {
        String message = createExceptionMessage(exception, information, adapter, "Read elements");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return List.of();
    }

    @Override
    public List<ID> handleReadIds(
        Exception exception,
        RepositoryModel<T, ID> information,
        SelectQuery query,
        RepositoryAdapter<T, ID, C> adapter
    ) {
        String message = createExceptionMessage(exception, information, adapter, "Read ids");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return List.of();
    }

    private static void checkForUnrecoverableErrors(
        @NotNull Exception exception,
        String message
    ) {
        if (exception.getMessage() != null
            && exception.getMessage().contains("Communications link failure")) {
            throw new RepositoryException(message, exception);
        }
    }

    private static <T, ID, C> @NotNull String createExceptionMessage(
        @NotNull Exception exception,
        @NotNull RepositoryModel<T, ID> information,
        @NotNull RepositoryAdapter<T, ID, C> adapter,
        String operation
    ) {
        return operation + " exception in repository [" + information.tableName()
            + "] with adapter [" + adapter.getClass().getSimpleName()
            + "]: " + exception.getMessage();
    }
}