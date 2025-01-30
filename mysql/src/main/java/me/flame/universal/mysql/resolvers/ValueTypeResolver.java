package me.flame.universal.mysql.resolvers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface ValueTypeResolver {
    Object resolve(ResultSet resultSet, String parameter) throws Exception;

    void insert(PreparedStatement preparedStatement, int parameter, Object value) throws Exception;

    Class<?> encodedType();
}
