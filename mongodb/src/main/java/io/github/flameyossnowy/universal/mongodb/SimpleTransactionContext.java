package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.ClientSession;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.TransactionClosedException;
import org.jetbrains.annotations.NotNull;

public class SimpleTransactionContext implements TransactionContext<ClientSession> {
    private final ClientSession connection;
    private boolean commited = false;

    public SimpleTransactionContext(@NotNull ClientSession connection) {
        this.connection = connection;
        connection.startTransaction();
    }

    @Override
    public ClientSession connection() {
        return connection;
    }

    @Override
    public TransactionResult<Boolean> commit() {
        if (!commited) {
            if (connection.hasActiveTransaction()) connection.commitTransaction();
            else return TransactionResult.failure(new TransactionClosedException("Transaction was closed."));
        }
        commited = true;
        return TransactionResult.success(true);
    }

    @Override
    public void rollback() {
        if (!commited) return;
        connection.abortTransaction();
    }

    @Override
    public void close() {
        rollback();
        connection.close();
    }
}