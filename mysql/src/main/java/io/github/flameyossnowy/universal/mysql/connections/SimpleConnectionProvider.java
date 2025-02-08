package io.github.flameyossnowy.universal.mysql.connections;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;

@SuppressWarnings("unused")
public class SimpleConnectionProvider implements ConnectionProvider<Connection> {
    public static final String BASE_URL = "jdbc:mysql://%s:%s/%s";
    private final String url;

    public SimpleConnectionProvider(final @NotNull MySQLCredentials credentials, final EnumSet<Optimizations> optimizations) {
        StringBuilder url = new StringBuilder(String.format(BASE_URL, credentials.host(), credentials.port(), credentials.database()));

        boolean first = true;
        for (Map.Entry<String, String> entry : credentials.dataSourceProperties().entrySet()) {
            if (first) {
                url.append('?');
                first = false;
            } else {
                url.append('&');
            }
            url.append(entry.getKey()).append('=').append(entry.getValue());
        }
        this.url = url.toString();

    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    @Override
    public void close() {
    }
}
