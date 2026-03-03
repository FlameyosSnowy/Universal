package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.RelationshipModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.lang.model.element.Modifier;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single Responsibility: generate the three relationship-loading methods
 * ({@code oneToOne}, {@code oneToMany}, {@code manyToOne}) with their
 * per-entity, per-field caching logic.
 */
public final class RelationshipMethodGenerator {

    public RelationshipMethodGenerator() {}

    // ------------------------------------------------------------------

    public MethodSpec generateOneToOne(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        MethodSpec.Builder m = MethodSpec.methodBuilder("oneToOne")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(e)
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() != RelationshipKind.ONE_TO_ONE) continue;
            emitSingleEntityCase(m, rel, "oneToOneCache", "handleOneToOneRelationship", e);
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown OneToOne field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateManyToOne(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        MethodSpec.Builder m = MethodSpec.methodBuilder("manyToOne")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(e)
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() != RelationshipKind.MANY_TO_ONE) continue;
            emitSingleEntityCase(m, rel, "manyToOneCache", "handleManyToOneRelationship", e);
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown ManyToOne field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateOneToMany(RepositoryModel repo, TypeName idType) {
        TypeVariableName e  = TypeVariableName.get("E");
        ClassName collection = ClassName.get(Collection.class);

        MethodSpec.Builder m = MethodSpec.methodBuilder("oneToMany")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(ParameterizedTypeName.get(collection, e))
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() != RelationshipKind.ONE_TO_MANY) continue;
            m.addCode("case $S: {\n", rel.fieldName());
            m.addStatement("$T field = repositoryModel.fieldByName(fieldName)",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel"));
            m.beginControlFlow(
                "if (field.consistency() == io.github.flameyossnowy.universal.api.annotations.enums.Consistency.STRONG)");
            m.addStatement("return ($T) handler.handleOneToManyRelationship(id, field)",
                ParameterizedTypeName.get(collection, e));
            m.endControlFlow();
            m.addStatement(
                "return ($T) oneToManyCache"
                + ".computeIfAbsent(id, f -> new $T<>())"
                + ".computeIfAbsent(fieldName, k -> handler.handleOneToManyRelationship(id, field))",
                ParameterizedTypeName.get(collection, e),
                ConcurrentHashMap.class);
            m.addCode("}\n");
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown OneToMany field: ");
        m.endControlFlow();
        return m.build();
    }

    // ------------------------------------------------------------------ helpers

    private static void emitSingleEntityCase(MethodSpec.Builder m, RelationshipModel rel,
                                              String cacheField, String handlerMethod,
                                              TypeVariableName e) {
        m.addCode("case $S: {\n", rel.fieldName());
        m.addStatement("$T field = repositoryModel.fieldByName(fieldName)",
            ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel"));
        m.beginControlFlow(
            "if (field.consistency() == io.github.flameyossnowy.universal.api.annotations.enums.Consistency.STRONG)");
        m.addStatement("return ($T) handler.$L(id, field)", e, handlerMethod);
        m.endControlFlow();
        m.addStatement(
            "return ($T) $L"
            + ".computeIfAbsent(id, f -> new $T<>())"
            + ".computeIfAbsent(fieldName, k -> handler.$L(id, field))",
            e, cacheField, ConcurrentHashMap.class, handlerMethod);
        m.addCode("}\n");
    }
}
