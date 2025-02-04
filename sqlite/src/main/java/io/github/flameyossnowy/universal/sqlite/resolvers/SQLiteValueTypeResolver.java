package io.github.flameyossnowy.universal.sqlite.resolvers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface SQLiteValueTypeResolver<T> {
    T resolve(ResultSet resultSet, String columnLabel) throws Exception;

    void insert(PreparedStatement statement, int parameterIndex, T value) throws Exception;

    Class<?> encodedType();
}

