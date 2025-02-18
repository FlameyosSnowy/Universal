package io.github.flameyossnowy.universal.sql.resolvers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface SQLValueTypeResolver<T> {
    T resolve(ResultSet resultSet, String columnLabel) throws Exception;

    void insert(PreparedStatement statement, int parameterIndex, T value) throws Exception;

    Class<?> encodedType();
}

