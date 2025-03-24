package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;

public interface SQLConnectionProvider extends ConnectionProvider<Connection> {
    PreparedStatement prepareStatement(String sql, Connection connection) throws Exception;
}
