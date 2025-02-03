package io.github.flameyossnowy.universal.sqlite.resolvers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface SQLiteValueTypeResolver {
    Object resolve(ResultSet resultSet, String parameter) throws Exception;

    void insert(PreparedStatement preparedStatement, int parameter, Object value) throws Exception;

    Class<?> encodedType();
}
