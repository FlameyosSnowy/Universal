package io.github.flameyossnowy.universal.sqlite.credentials;

import java.util.Map;

public record SQLiteCredentialsImpl(String directory,
                                    int poolSize,
                                    Map<String, String> dataSourceProperties,
                                    int minimumIdle,
                                    long idleTimeout,
                                    long connectionTimeout,
                                    String jdbcUrl) implements SQLiteCredentials {}