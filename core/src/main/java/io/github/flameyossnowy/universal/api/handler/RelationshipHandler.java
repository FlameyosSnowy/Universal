package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.reflect.FieldData;

import java.util.List;

/**
 * Generic, portable relationship handler API.
 * Defines abstract relationship accessors that any backend can implement.
 */
public interface RelationshipHandler<ID> {
    Object handleManyToOneRelationship(ID primaryKeyValue, FieldData<?> field);

    Object handleOneToOneRelationship(ID primaryKeyValue, FieldData<?> field);

    List<Object> handleOneToManyRelationship(FieldData<?> field, ID primaryKeyValue);
}
