package me.flame.universal.mysql.credentials;

import java.util.HashMap;
import java.util.Map;

public sealed interface MySQLCredentials permits MySQLCredentialsImpl {

    static Builder builder() {
        return new Builder();
    }

    int poolSize();

    Map<String, String> dataSourceProperties();

    int minimumIdle();

    long idleTimeout();

    long connectionTimeout();

    String jdbcUrl();

    class Builder {
        private String host;
        private int port;
        private String database;
        private String username;
        private String password;
        private boolean ssl = false;
        private String driver = "com.mysql.cj.jdbc.Driver";
        private int poolSize;
        private final Map<String, String> dataSourceProperties = new HashMap<>();
        private int minimumIdle = 2;
        private long idleTimeout = 30000;
        private long connectionTimeout = 30000;

        public Builder poolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        public Builder driver(String driver) {
            this.driver = driver;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder dataSourceProperty(String key, String value) {
            this.dataSourceProperties.put(key, value);
            return this;
        }

        public Builder dataSourceProperties(Map<String, Object> properties) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                this.dataSourceProperties.put(entry.getKey(), entry.getValue().toString());
            }
            return this;
        }

        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder connectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        private String buildJdbcUrl() {
            StringBuilder url = new StringBuilder("jdbc:mysql://")
                    .append(host)
                    .append(":")
                    .append(port)
                    .append("/")
                    .append(database)
                    .append("?useSSL=")
                    .append(ssl);

            for (Map.Entry<String, String> entry : dataSourceProperties.entrySet()) {
                url.append("&").append(entry.getKey()).append("=").append(entry.getValue());
            }

            return url.toString();
        }

        public MySQLCredentials build() {
            String jdbcUrl = buildJdbcUrl();
            return new MySQLCredentialsImpl(
                    host, port, database, username, password, ssl, driver, type,
                    poolSize, dataSourceProperties, minimumIdle, idleTimeout, connectionTimeout, jdbcUrl
            );
        }
    }

    String host();

    int port();

    String database();

    String username();

    String password();

    boolean ssl();

    String driver();

    String type();
}