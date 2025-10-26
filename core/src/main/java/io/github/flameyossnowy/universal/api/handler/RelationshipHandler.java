package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * Generic, portable relationship handler API.
 * Defines abstract relationship accessors that any backend can implement.
 */
public interface RelationshipHandler<T, ID, R> {
    Object handleManyToOneRelationship(Object relationKey, FieldData<?> field);

    Object handleOneToOneRelationship(ID primaryKeyValue, FieldData<?> field);

    List<Object> handleOneToManyRelationship(FieldData<?> field, ID primaryKeyValue);
}
