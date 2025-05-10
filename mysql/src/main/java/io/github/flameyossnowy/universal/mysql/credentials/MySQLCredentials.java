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
    private int poolSize = 4;
    private int minimumIdle = 2;
    private long idleTimeout = 30000;
    private long connectionTimeout = 30000;
    private Consumer<MysqlDataSource> dataSourceConsumer = (dataSource) -> {};

    /**
     * The constructor of MySQLCredentials
     * @param host The host of the database, such as localhost
     * @param port The port of the database
     * @param database The name of the database
     * @param username The username of the database
     * @param password The password of the database
     */
    public MySQLCredentials(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    /**
     * Sets the pool size for the MySQL connection.
     * <p>Has no effects on non-pooled implementations</p>
     * @param poolSize The maximum number of connections that the pool can contain.
     * @return The current instance of MySQLCredentials for method chaining.
     */
    public MySQLCredentials setPoolSize(int poolSize) {
        this.poolSize = poolSize;
        return this;
    }

    /**
     * Sets the minimum number of idle connections that the pool can contain.
     * <p>Has no effects on non-pooled implementations</p>
     * @param minimumIdle The minimum number of idle connections that the pool can contain.
     * @return The current instance of MySQLCredentials for method chaining.
     */
    public MySQLCredentials setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
        return this;
    }

    public MySQLCredentials setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    /**
     * Sets the timeout for establishing a connection.
     * <p>Has no effects on non-pooled implementations</p>
     * @param connectionTimeout The timeout in milliseconds for establishing a connection.
     * @return The current instance of MySQLCredentials for method chaining.
     */
    public MySQLCredentials setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Enables or disables SSL/TLS encryption for the connection.
     * @param ssl Whether to use SSL/TLS encryption for the connection.
     * @return The current instance of MySQLCredentials for method chaining.
     */
    public MySQLCredentials setSsl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    /**
     * Sets a custom consumer for the MySQL data source.
     * <p>This consumer allows for additional configuration of the
     * MysqlDataSource before it is used to establish a connection.</p>
     * @param consumer A Consumer that accepts a MysqlDataSource instance
     *                 and performs additional configurations on it.
     * @return The current instance of MySQLCredentials for method chaining.
     */
    public MySQLCredentials setDataSourceConsumer(Consumer<MysqlDataSource> consumer) {
        this.dataSourceConsumer = consumer;
        return this;
    }

    /**
     * Gets the hostname of the MySQL server.
     * @return The hostname of the MySQL server, or {@code null} if one was not specified.
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port number that the MySQL server is listening on.
     * @return The port number that the MySQL server is listening on, or
     *         0 if the default port (3306) is being used.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the name of the database that the credentials are for.
     * @return The name of the database that the credentials are for.
     */
    public String getDatabase() {
        return database;
    }

    /**
     * Gets the username that is used to connect to the MySQL server.
     * @return The username that is used to connect to the MySQL server.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password that is used to connect to the MySQL server.
     * @return The password that is used to connect to the MySQL server.
     */
    public String getPassword() {
        return password;
    }

    public boolean isSsl() {
        return ssl;
    }

    /**
     * Retrieves the maximum number of connections that the pool can contain.
     * @return The pool size, indicating the maximum number of connections.
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Gets the minimum number of idle connections in the pool that HikariCP should attempt to maintain.
     * @return The minimum number of idle connections in the pool.
     */
    public int getMinimumIdle() {
        return minimumIdle;
    }

    /**
     * Gets the maximum amount of time that a connection can be idle before it is closed
     * and removed from the pool.
     * <p>Has no effects on non-pooled implementations</p>
     * @return The maximum amount of time that a connection can be idle before it is closed
     *         and removed from the pool, in milliseconds.
     */
    public long getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Retrieves the timeout for establishing a connection.
     * <p>Has no effects on non-pooled implementations</p>
     * @return The timeout in milliseconds for establishing a connection.
     */
    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Gets the consumer that will be called with an instance of {@link MysqlDataSource} that is used to set up the connection
     * to the MySQL server. This can be used to customize the connection in ways that this class does not support.
     * @return The consumer that will be called with an instance of {@link MysqlDataSource} that is used to set up the connection
     *         to the MySQL server.
     */
    public Consumer<MysqlDataSource> getDataSourceConsumer() {
        return dataSourceConsumer;
    }
}
