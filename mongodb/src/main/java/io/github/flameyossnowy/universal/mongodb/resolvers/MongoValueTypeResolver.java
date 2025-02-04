package io.github.flameyossnowy.universal.mongodb.resolvers;

import org.bson.Document;

public interface MongoValueTypeResolver<T> {
    T resolve(Document resultSet, String parameter) throws Exception;

    void insert(Document preparedStatement, String parameter, T value) throws Exception;

    Class<?> encodedType();
}
