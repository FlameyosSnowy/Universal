package io.github.flameyossnow.universal.cassandra;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.QueryExecutionException;
import com.datastax.driver.core.exceptions.QueryValidationException;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

public class CassandraTransactionContext implements TransactionContext<Session> {
    private final Session session;
    private boolean isClosed = false;
    private boolean isCommitted = false;

    public CassandraTransactionContext(Session session) {
        this.session = session;
        this.session.execute("BEGIN BATCH");
    }

    @Override
    public Session connection() {
        if (isClosed) {
            throw new IllegalStateException("Transaction context is closed");
        }
        return session;
    }

    @Override
    public void close() {
        if (!isClosed) {
            try {
                if (!isCommitted) {
                    rollback();
                }
            } finally {
                isClosed = true;
            }
        }
    }

    @Override
    public TransactionResult<Boolean> commit() {
        if (isClosed) {
            return TransactionResult.failure(new IllegalStateException("Transaction context is already closed"));
        }
        
        try {
            session.execute("APPLY BATCH");
            isCommitted = true;
            return TransactionResult.success(true);
        } catch (NoHostAvailableException | QueryExecutionException | QueryValidationException e) {
            return TransactionResult.failure(e);
        } finally {
            close();
        }
    }

    @Override
    public void rollback() {
        if (isClosed) {
            throw new IllegalStateException("Transaction context is already closed");
        }
        
        try {
            session.execute("ROLLBACK");
        } finally {
            isClosed = true;
        }
    }
}
