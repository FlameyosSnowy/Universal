package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.reflect.FieldData;

import java.util.List;
import java.util.Set;

/**
 * Generic, portable relationship handler API.
 * Defines abstract relationship accessors that any backend can implement.
 */
public interface RelationshipHandler<T, ID> {
    Object handleManyToOneRelationship(ID primaryKeyValue, FieldData<?> field);

    Object handleOneToOneRelationship(ID primaryKeyValue, FieldData<?> field);

    List<Object> handleOneToManyRelationship(FieldData<?> field, ID primaryKeyValue);

    void prefetch(Iterable<?> results, Set<String> prefetch);

    void invalidateRelationshipsForId(ID id);

    void clear();
}
