package io.github.flameyossnowy.universal.mysql.credentials;

import com.mysql.cj.jdbc.MysqlDataSource;

import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class MySQLCredentials {
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
    private Consumer<MysqlDataSource> dataSourceConsumer = (dataSource) -> {};

    public MySQLCredentials(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public MySQLCredentials setPoolSize(int poolSize) {
        this.poolSize = poolSize;
        return this;
    }

    public MySQLCredentials setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
        return this;
    }

    public MySQLCredentials setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    public MySQLCredentials setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public MySQLCredentials setSsl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public MySQLCredentials setDriver(String driver) {
        this.driver = driver;
        return this;
    }

    public MySQLCredentials setDataSourceConsumer(Consumer<MysqlDataSource> consumer) {
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

    public Consumer<MysqlDataSource> getDataSourceConsumer() {
        return dataSourceConsumer;
    }
}
