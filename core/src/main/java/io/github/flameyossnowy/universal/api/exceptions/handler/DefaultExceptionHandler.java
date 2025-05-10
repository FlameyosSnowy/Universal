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
        String message = String.format("Exception in repository [%s] with adapter [%s]: %s",
                information.getRepositoryName(),
                adapter.getClass().getSimpleName(),
                exception.getMessage());

        if (exception.getMessage() != null && exception.getMessage().contains("Communications link failure")) {
            // communications exception should not be retried, this is an acceptable moment to throw
            throw new RepositoryException(message, exception);
        }

        Logging.error(message);

        throw new RepositoryException(message, exception);
        //return TransactionResult.failure(new RepositoryException(message, exception));
    }

    @Override
    public TransactionResult<Boolean> handleDelete(Exception exception, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter) {
        String message = String.format("Exception in repository [%s] with adapter [%s]: %s",
                information.getRepositoryName(),
                adapter.getClass().getSimpleName(),
                exception.getMessage());

        if (exception.getMessage().contains("Communications link failure")) {
            // communications exception should not be retried, this is an acceptable moment to throw
            throw new RepositoryException(message, exception);
        }

        Logging.error(message);

        throw new RepositoryException(message, exception);
        //return TransactionResult.failure(new RepositoryException(message, exception));
    }

    @Override
    public TransactionResult<Boolean> handleUpdate(Exception exception, RepositoryInformation information, RepositoryAdapter<T, ID, C> adapter) {
        String message = String.format("Exception in repository [%s] with adapter [%s]: %s",
                information.getRepositoryName(),
                adapter.getClass().getSimpleName(),
                exception.getMessage());

        if (exception.getMessage().contains("Communications link failure")) {
            // communications exception should not be retried, this is an acceptable moment to throw
            throw new RepositoryException(message, exception);
        }

        Logging.error(message);

        throw new RepositoryException(message, exception);

        //return TransactionResult.failure(new RepositoryException(message, exception));
    }

    @Override
    public List<T> handleRead(Exception exception, RepositoryInformation information, SelectQuery query, RepositoryAdapter<T, ID, C> adapter) {
        String message = String.format("Exception in repository [%s] with adapter [%s]: %s",
                information.getRepositoryName(),
                adapter.getClass().getSimpleName(),
                exception.getMessage());

        if (exception.getMessage().contains("Communications link failure")) {
            // communications exception should not be retried, this is an acceptable moment to throw
            throw new RepositoryException(message, exception);
        }

        Logging.error(message);

        throw new RepositoryException(message, exception);
        //return List.of();
    }

    /*private static boolean isPostgreSQLCommunicationsException(Exception e) {
        try {
            Class<?> clazz = Class.forName("org.postgresql.util.PSQLException");
            if (!clazz.isInstance(e)) return false;

            java.lang.reflect.Method getSQLState = clazz.getMethod("getSQLState");
            Object sqlState = getSQLState.invoke(e);
            return "08006".equals(sqlState);
        } catch (ClassNotFoundException | NoSuchMethodException |
                 IllegalAccessException | java.lang.reflect.InvocationTargetException ex) {
            // Can't inspect the exception safely
            return false;
        }
    }*/
}
