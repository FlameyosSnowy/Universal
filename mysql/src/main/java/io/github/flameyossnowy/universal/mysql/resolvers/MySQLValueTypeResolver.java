package io.github.flameyossnowy.universal.mysql.resolvers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface MySQLValueTypeResolver<T> {
    T resolve(ResultSet resultSet, String parameter) throws Exception;

    void insert(PreparedStatement preparedStatement, int parameter, T value) throws Exception;

    Class<?> encodedType();
}
