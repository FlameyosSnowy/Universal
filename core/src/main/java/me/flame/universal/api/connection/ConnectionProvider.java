package me.flame.universal.api.connection;

public interface ConnectionProvider<C> extends AutoCloseable {
    C getConnection() throws Exception;
}

