package io.github.flameyossnowy.universal.sqlite.credentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public interface SQLiteCredentials {

    static Builder builder() {
        return new Builder();
    }

    String jdbcUrl();

    String directory();

    int poolSize();

    Map<String, String> dataSourceProperties();

    int minimumIdle();

    long idleTimeout();

    long connectionTimeout();

    class Builder {
        private String directory;
        private int poolSize = 2;
        private final Map<String, String> dataSourceProperties = new HashMap<>();
        private int minimumIdle = 2;
        private long idleTimeout = 30000;
        private long connectionTimeout = 30000;

        public Builder directory(String directory) {
            this.directory = directory;
            return this;
        }

        public Builder poolSize(int poolSize) {
            this.poolSize = poolSize;
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


        public SQLiteCredentials build() {
            if (directory == null || directory.isBlank()) {
                throw new IllegalArgumentException("Directory path must be specified for SQLite credentials.");
            }

            Path filePath = Path.of(directory);
            try {
                Path parentDir = filePath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }

                if (!Files.exists(filePath)) {
                    Files.createFile(filePath); // Create the database file
                }
            } catch (IOException e) {
                throw new RuntimeException("Error validating or creating SQLite database file: " + filePath, e);
            }
            return new SQLiteCredentialsImpl(directory, poolSize, dataSourceProperties, minimumIdle, idleTimeout, connectionTimeout, "jdbc:sqlite:" + directory);
        }
    }
}
