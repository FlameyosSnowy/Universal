package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.TypeName;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils;

import javax.lang.model.type.TypeMirror;

public record RelationshipModel(
    RelationshipKind relationshipKind,
    String fieldName,
    String columnName,
    String targetEntityQualifiedName,
    String mappedBy,
    boolean lazy,
    boolean owning,
    String setterName,
    String getterName,
    String loaderMethod,

    TypeMirror fieldType,
    TypeMirror targetType,

    TypeName fieldTypeName,
    TypeName targetTypeName,

    CollectionKind collectionKind,
    Consistency consistency
) {
    public static RelationshipModel create(
        RelationshipKind kind,
        String fieldName,
        String columnName,
        TypeMirror fieldType,
        TypeMirror targetType,
        String mappedBy,
        boolean lazy,
        CollectionKind collectionKind,
        Consistency consistency
    ) {
        String cap = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        String loader = switch (kind) {
            case ONE_TO_ONE  -> "oneToOne";
            case ONE_TO_MANY -> "oneToMany";
            case MANY_TO_ONE -> "manyToOne";
        };

        boolean owning = switch (kind) {
            case MANY_TO_ONE -> true;
            case ONE_TO_MANY -> false;
            case ONE_TO_ONE  -> (mappedBy == null || mappedBy.isBlank());
        };

        return new RelationshipModel(
            kind,
            fieldName,
            columnName,
            TypeMirrorUtils.qualifiedName(targetType),
            mappedBy,
            lazy,
            owning,
            "set" + cap,
            "get" + cap,
            loader,
            fieldType,
            targetType,
            TypeName.get(fieldType),
            TypeName.get(targetType),
            collectionKind,
            consistency
        );
    }

    public boolean hasField(io.github.flameyossnowy.universal.checker.FieldModel field) {
        return field != null
            && fieldName.equals(field.name())
            && columnName.equals(field.columnName());
    }
}