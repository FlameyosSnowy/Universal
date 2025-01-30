package me.flame.universal.sqlite.credentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record SQLiteCredentialsImpl(String directory,
                                    int poolSize,
                                    Map<String, String> dataSourceProperties,
                                    int minimumIdle,
                                    long idleTimeout,
                                    long connectionTimeout,
                                    String jdbcUrl) implements SQLiteCredentials {}