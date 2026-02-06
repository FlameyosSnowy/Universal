package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import org.jetbrains.annotations.Nullable;

public interface RelationshipModel<T, ID> {
    String fieldName();

    RelationshipKind relationshipKind();

    boolean lazy();

    boolean isCollection();

    CollectionKind collectionKind();

    Class<?> targetEntityType();

    @Nullable
    RelationshipModel<?, ?> getTargetRelationshipModel();

    FieldModel<T> getFieldModel();

    @Nullable
    FieldModel<?> getOwningFieldModel();

    boolean isOwning();

    Object get(T entity);

    void set(T entity, Object value);

    boolean cascadesInsert();

    boolean cascadesUpdate();

    boolean cascadesDelete();
}
