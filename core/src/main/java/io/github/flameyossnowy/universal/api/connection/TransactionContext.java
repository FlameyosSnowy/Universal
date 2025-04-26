package io.github.flameyossnowy.universal.api.connection;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;

public interface TransactionContext<C> extends AutoCloseable {
    C connection();

    void close();

    TransactionResult<Void> commit();

    void rollback() throws Exception;
}
