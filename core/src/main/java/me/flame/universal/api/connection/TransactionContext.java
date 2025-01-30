package me.flame.universal.api.connection;

public interface TransactionContext<C> extends AutoCloseable {
    C connection();

    void commit() throws Exception;

    void rollback() throws Exception;
}
