package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.ClientSession;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

public class SimpleTransactionContext implements TransactionContext<ClientSession> {
    private final ClientSession connection;
    private boolean commited = false;

    public SimpleTransactionContext(ClientSession connection) {
        this.connection = connection;
        connection.startTransaction();
    }

    @Override
    public ClientSession connection() {
        return connection;
    }

    @Override
    public void commit() {
        connection.commitTransaction();
        commited = true;
    }

    @Override
    public void rollback() {
        if (!commited) connection.abortTransaction();
    }

    @Override
    public void close() {
        rollback();
        connection.close();
    }
}
