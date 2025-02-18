package io.github.flameyossnowy.universal.sql.resolvers;

import java.util.Collection;

public interface CollectionTypeResolver<T, ID> {
    Collection<T> resolve(ID id) throws Exception;

    void insert(ID id, Collection<T> collection) throws Exception;

    void insert(ID id, T value) throws Exception;

    void delete(ID id, T value) throws Exception;

    void delete(ID id) throws Exception;

    Class<T> getElementType();
}
