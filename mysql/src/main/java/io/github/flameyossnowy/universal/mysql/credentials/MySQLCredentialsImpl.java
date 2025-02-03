package io.github.flameyossnowy.universal.mysql.credentials;

import java.util.Map;

public record MySQLCredentialsImpl(
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean ssl,
        String driver,
        int poolSize,
        Map<String, String> dataSourceProperties,
        int minimumIdle,
        long idleTimeout,
        long connectionTimeout
) implements MySQLCredentials {}