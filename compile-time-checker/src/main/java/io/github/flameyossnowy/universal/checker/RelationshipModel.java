package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.TypeName;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils;

import javax.lang.model.type.TypeMirror;

public record RelationshipModel(
    RelationshipKind relationshipKind,
    String fieldName,
    String targetEntityQualifiedName,
    String mappedBy,
    boolean lazy,
    String setterName,
    String getterName,
    String loaderMethod,

    TypeMirror fieldType,        // ← declared field type
    TypeMirror targetType,       // ← entity type

    TypeName fieldTypeName,      // ← List<Faction>
    TypeName targetTypeName,     // ← Faction

    CollectionKind collectionKind,

    Consistency consistency
) {
    public static RelationshipModel create(
        RelationshipKind kind,
        String fieldName,
        TypeMirror fieldType,
        TypeMirror targetType,
        String mappedBy,
        boolean lazy,
        CollectionKind collectionKind,
        Consistency consistency
    ) {
        String cap =
            Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        String setter = "set" + cap;
        String getter = "get" + cap;

        String loader = switch (kind) {
            case ONE_TO_ONE -> "oneToOne";
            case ONE_TO_MANY -> "oneToMany";
            case MANY_TO_ONE -> "manyToOne";
        };

        return new RelationshipModel(
            kind,
            fieldName,
            TypeMirrorUtils.qualifiedName(targetType),
            mappedBy,
            lazy,
            setter,
            getter,
            loader,
            fieldType,
            targetType,
            TypeName.get(fieldType),
            TypeName.get(targetType),
            collectionKind,
            consistency
        );
    }
}
