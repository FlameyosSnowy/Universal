package io.github.flameyossnowy.universal.api.connection;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;

/**
 * TransactionContext is used to manage a transaction for a connection.
 * <p>
 * It is best suited for repositories that use a connection to perform operations that can be batched and can be used to perform a commit or rollback.
 * @param <C> The type of connection being managed by this transaction context.
 * @author flameyosflow
 * @version 3.0.0
 */
public interface TransactionContext<C> extends AutoCloseable {
    /**
     * Get the connection being managed by this transaction context.
     *
     * @return The connection
     */
    C connection();

    /**
     * Closes the transaction context, and the underlying connection. This will
     * rollback the transaction if not already committed.
     */
    void close();

    /**
     * Commits the transaction. If the commit is successful, the underlying
     * connection will be closed and the transaction context will be
     * invalidated.
     *
     * @return a result object that can be used to find out if the commit was
     * successful or not which includes the exception if the commit failed.
     */
    TransactionResult<Boolean> commit();

    /**
     * Rolls back the transaction. If the rollback is successful, the underlying
     * connection will be closed and the transaction context will be
     * invalidated.
     *
     * @throws Exception If the rollback failed.
     */
    void rollback() throws Exception;
}
