package io.github.flameyossnowy.universal.sqlite.credentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class SQLiteCredentials {
    private final String directory;
    private int poolSize = 2;
    private final Map<String, String> dataSourceProperties = new HashMap<>();
    private int minimumIdle = 2;
    private long idleTimeout = 30000;
    private long connectionTimeout = 30000;
    private final String jdbcUrl;

    public SQLiteCredentials(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Directory path must be specified for SQLite credentials.");
        }
        this.directory = directory;
        this.jdbcUrl = "jdbc:sqlite:" + directory;
        this.validateOrCreateDatabaseFile();
    }

    private void validateOrCreateDatabaseFile() {
        Path filePath = Path.of(directory);
        try {
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error validating or creating SQLite database file: " + filePath, e);
        }
    }

    public SQLiteCredentials setPoolSize(int poolSize) {
        this.poolSize = poolSize;
        return this;
    }

    public SQLiteCredentials setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
        return this;
    }

    public SQLiteCredentials setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    public SQLiteCredentials setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public SQLiteCredentials addDataSourceProperty(String key, String value) {
        this.dataSourceProperties.put(key, value);
        return this;
    }

    public SQLiteCredentials addDataSourceProperties(Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            this.dataSourceProperties.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getDirectory() {
        return directory;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public Map<String, String> getDataSourceProperties() {
        return dataSourceProperties;
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
}
