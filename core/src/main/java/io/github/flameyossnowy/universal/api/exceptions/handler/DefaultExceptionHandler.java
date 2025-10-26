package io.github.flameyossnowy.universal.api.exceptions.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DefaultExceptionHandler<T, ID, C> implements ExceptionHandler<T, ID, C> {
    @Override
    public TransactionResult<Boolean> handleInsert(@NotNull Exception exception, @NotNull RepositoryInformation information, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        return handleException(exception, information, adapter);
    }

    @Override
    public TransactionResult<Boolean> handleDelete(Exception exception, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter) {
        return handleException(exception, information, adapter);
    }

    @Override
    public TransactionResult<Boolean> handleUpdate(Exception exception, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter) {
        return handleException(exception, information, adapter);
    }

    @NotNull
    private TransactionResult<Boolean> handleException(Exception exception, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter) {
        String message = createExceptionMessage(exception, information, adapter);
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message);
        return TransactionResult.failure(new RepositoryException(message, exception));
    }

    @Override
    public List<T> handleRead(@NotNull Exception exception, @NotNull RepositoryInformation information, SelectQuery query, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        String message = createExceptionMessage(exception, information, adapter);
        checkForUnrecoverableErrors(exception, message);
        Logging.error(message);
        return List.of();
    }

    private static void checkForUnrecoverableErrors(@NotNull Exception exception, String message) {
        if (exception.getMessage() != null && exception.getMessage().contains("Communications link failure")) {
            // communications exception should not be retried, this is an acceptable moment to throw
            throw new RepositoryException(message, exception);
        }
    }

    private static <T, ID, C> @NotNull String createExceptionMessage(@NotNull Exception exception, @NotNull RepositoryInformation information, @NotNull RepositoryAdapter<T, ID, C> adapter) {
        return String.format("Exception in repository [%s] with adapter [%s]: %s",
                information.getRepositoryName(),
                adapter.getClass().getSimpleName(),
                exception.getMessage());
    }
}
