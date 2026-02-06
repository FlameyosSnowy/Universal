package io.github.flameyossnowy.universal.api.exceptions.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DefaultExceptionHandler<T, ID, C> implements ExceptionHandler<T, ID, C> {
    @Override
    public TransactionResult<Boolean> handleInsert(@NotNull Exception exception, @NotNull RepositoryModel<T, ID> information, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        return handleException(exception, information, adapter, "Insert");
    }

    @Override
    public TransactionResult<Boolean> handleDelete(Exception exception, RepositoryModel<T, ID> information, RepositoryAdapter<T, ID, C> adapter) {
        return handleException(exception, information, adapter, "Delete");
    }

    @Override
    public TransactionResult<Boolean> handleUpdate(Exception exception, RepositoryModel<T, ID> information, RepositoryAdapter<T, ID, C> adapter) {
        return handleException(exception, information, adapter, "Update");
    }

    @NotNull
    private TransactionResult<Boolean> handleException(Exception exception, RepositoryModel<T, ID> information, RepositoryAdapter<T, ID, C> adapter, String update) {
        String message = createExceptionMessage(exception, information, adapter, update);
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return TransactionResult.failure(new RepositoryException(message, exception));
    }

    @Override
    public List<T> handleRead(@NotNull Exception exception, @NotNull RepositoryModel<T, ID> information, SelectQuery query, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        String message = createExceptionMessage(exception, information, adapter, "Read elements");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return List.of();
    }

    @Override
    public List<ID> handleReadIds(Exception exception, RepositoryModel<T, ID> information, SelectQuery query, RepositoryAdapter<T, ID, C> adapter) {
        String message = createExceptionMessage(exception, information, adapter, "Read ids");
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message, exception);
        return List.of();
    }

    private static void checkForUnrecoverableErrors(@NotNull Exception exception, String message) {
        if (exception.getMessage() != null && exception.getMessage().contains("Communications link failure")) {
            // communications exception should not be retried, this is an acceptable moment to throw
            throw new RepositoryException(message, exception);
        }
    }

    private static <T, ID, C> @NotNull String createExceptionMessage(@NotNull Exception exception, @NotNull RepositoryModel<T, ID> information, @NotNull RepositoryAdapter<T, ID, C> adapter, String update) {
        return update + " exception in repository [" + information.tableName() + "] with adapter [" + adapter.getClass().getSimpleName() + "]: " + exception.getMessage();
    }
}
