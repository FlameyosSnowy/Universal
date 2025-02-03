package io.github.flameyossnowy.universal.mongodb.resolvers;

import org.bson.Document;

public interface ValueTypeResolver {
    Object resolve(Document resultSet, String parameter) throws Exception;

    void insert(Document preparedStatement, String parameter, Object value) throws Exception;

    Class<?> encodedType();
}
