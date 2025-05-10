package io.github.flameyossnowy.universal.postgresql.credentials;

import org.postgresql.ds.PGSimpleDataSource;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class PostgreSQLCredentials {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private boolean ssl = false;
    private String driver = "com.mysql.cj.jdbc.Driver";
    private int poolSize = 4;
    private int minimumIdle = 2;
    private long idleTimeout = 30000;
    private long connectionTimeout = 30000;
    private Consumer<PGSimpleDataSource> dataSourceConsumer = (dataSource) -> {};

    public PostgreSQLCredentials(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public PostgreSQLCredentials setPoolSize(int poolSize) {
        this.poolSize = poolSize;
        return this;
    }

    public PostgreSQLCredentials setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
        return this;
    }

    public PostgreSQLCredentials setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    public PostgreSQLCredentials setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public PostgreSQLCredentials setSsl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public PostgreSQLCredentials setDriver(String driver) {
        this.driver = driver;
        return this;
    }

    public PostgreSQLCredentials setDataSourceConsumer(Consumer<PGSimpleDataSource> consumer) {
        this.dataSourceConsumer = consumer;
        return this;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public String getDriver() {
        return driver;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public Consumer<PGSimpleDataSource> getDataSourceConsumer() {
        return dataSourceConsumer;
    }
}
