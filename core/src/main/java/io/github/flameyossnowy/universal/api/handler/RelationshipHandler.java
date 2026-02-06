package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.meta.FieldModel;

import java.util.List;
import java.util.Set;

/**
 * Generic, portable relationship handler API.
 * Defines abstract relationship accessors that any backend can implement.
 */
public interface RelationshipHandler<T, ID> {
    Object handleManyToOneRelationship(ID primaryKeyValue, FieldModel<T> field);

    Object handleOneToOneRelationship(ID primaryKeyValue, FieldModel<T> field);

    List<Object> handleOneToManyRelationship(ID primaryKeyValue, FieldModel<T> field);

    void prefetch(Iterable<?> results, Set<String> prefetch);

    void invalidateRelationshipsForId(ID id);

    void clear();
}
