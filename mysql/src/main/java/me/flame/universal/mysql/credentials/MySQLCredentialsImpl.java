package me.flame.universal.mysql.credentials;

import java.util.Map;

public record MySQLCredentialsImpl(
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean ssl,
        String driver,
        String type,
        int poolSize,
        Map<String, String> dataSourceProperties,
        int minimumIdle,
        long idleTimeout,
        long connectionTimeout,
        String jdbcUrl
) implements MySQLCredentials {}