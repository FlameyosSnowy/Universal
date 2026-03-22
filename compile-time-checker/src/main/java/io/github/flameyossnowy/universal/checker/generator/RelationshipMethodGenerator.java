package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.RelationshipModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.lang.model.element.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RelationshipMethodGenerator {
    static final String NULL_SENTINEL_FIELD = "NULL_SENTINEL";

    public RelationshipMethodGenerator() {}

    public MethodSpec generateOneToOne(RepositoryModel repo, TypeName idType, TypeName entityType) {
        TypeVariableName e = TypeVariableName.get("E");
        MethodSpec.Builder m = MethodSpec.methodBuilder("oneToOne")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(e)
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(entityType, "entity")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() != RelationshipKind.ONE_TO_ONE) continue;
            if (rel.owning()) continue;
            emitSingleEntityCase(m, rel, "oneToOneCache", "handleOneToOneRelationship", e, true);
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown OneToOne field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateManyToOne(RepositoryModel repo, TypeName idType, TypeName entityType) {
        TypeVariableName e = TypeVariableName.get("E");
        MethodSpec.Builder m = MethodSpec.methodBuilder("manyToOne")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(e)
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(entityType, "entity")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() != RelationshipKind.MANY_TO_ONE) continue;
            emitSingleEntityCase(m, rel, "manyToOneCache", "handleManyToOneRelationship", e, false);
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown ManyToOne field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateOneToMany(RepositoryModel repo, TypeName idType) {
        TypeVariableName e   = TypeVariableName.get("E");
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

            m.addStatement("$T<$T, $T> inner = oneToManyCache.computeIfAbsent(id, f -> new $T<>())",
                Map.class, String.class, ParameterizedTypeName.get(Collection.class, Object.class), ConcurrentHashMap.class);
            m.addStatement("$T cached = inner.get(fieldName)", Collection.class);
            m.beginControlFlow("if (cached != null)");
            m.addStatement("return ($T) cached", ParameterizedTypeName.get(collection, e));
            m.endControlFlow();
            m.addStatement("$T loaded = handler.handleOneToManyRelationship(id, field)", Collection.class);
            m.addStatement("$T existing = inner.putIfAbsent(fieldName, loaded)", Collection.class);
            m.addStatement("return ($T) (existing != null ? existing : loaded)",
                ParameterizedTypeName.get(collection, e));

            m.addCode("}\n");
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown OneToMany field: ");
        m.endControlFlow();
        return m.build();
    }

    private static MethodSpec.Builder baseBuilder(String name, TypeVariableName e, TypeName idType) {
        return MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(e)
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");
    }

    /**
     * Emits a switch case for a single-entity relationship (OneToOne or ManyToOne).
     *
     * <p>Uses a sentinel object for cached nulls to distinguish "not yet loaded"
     * from "loaded and the result was null". The pattern is:
     * <pre>
     *   Map inner = cache.computeIfAbsent(id, ...);
     *   Object cached = inner.get(fieldName);
     *   if (cached != null) return cached == SENTINEL ? null : (E) cached;
     *   Object loaded = handler.handle...(id, field); // DB call outside any CHM write
     *   Object stored = loaded == null ? SENTINEL : loaded;
     *   Object existing = inner.putIfAbsent(fieldName, stored);
     *   return (E) (existing != null ? (existing == SENTINEL ? null : existing) : loaded);
     * </pre>
     */
    private static void emitSingleEntityCase(
        MethodSpec.Builder m,
        RelationshipModel rel,
        String cacheField,
        String handlerMethod,
        TypeVariableName e,
        boolean isOneToOne
    ) {
        m.addCode("case $S: {\n", rel.fieldName());
        m.addStatement("$T field = repositoryModel.fieldByName(fieldName)",
            ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel"));

        m.beginControlFlow(
            "if (field.consistency() == io.github.flameyossnowy.universal.api.annotations.enums.Consistency.STRONG)");

        if (isOneToOne && rel.owning()) {
            m.addStatement("$T fkValue = ($T) field.getValue(entity)", rel.targetTypeName(), rel.targetTypeName());
            m.addStatement("return ($T) handler.handleOneToOneRelationshipOwning(fkValue, field)", e);
        } else {
            m.addStatement("return ($T) handler.$L(id, field)", e, handlerMethod);
        }
        m.endControlFlow();

        m.addStatement("$T<$T, $T> inner = $L.computeIfAbsent(id, f -> new $T<>())",
            Map.class, String.class, Object.class, cacheField, ConcurrentHashMap.class);
        m.addStatement("$T cached = inner.get(fieldName)", Object.class);
        m.beginControlFlow("if (cached != null)");
        m.addStatement("return ($T) (cached == $L ? null : cached)", e, NULL_SENTINEL_FIELD);
        m.endControlFlow();

        if (isOneToOne && rel.owning()) {
            m.addStatement("$T fkValue = ($T) field.getValue(entity)", rel.targetTypeName(), rel.targetTypeName());
            m.addStatement("$T loaded = handler.handleOneToOneRelationshipOwning(fkValue, field)", Object.class);
        } else {
            m.addStatement("$T loaded = handler.$L(id, field)", Object.class, handlerMethod);
        }

        m.addStatement("$T stored = (loaded == null) ? $L : loaded", Object.class, NULL_SENTINEL_FIELD);
        m.addStatement("inner.put(fieldName, stored)");
        m.addStatement("return (E) ((stored == $L) ? null : stored)", NULL_SENTINEL_FIELD);

        m.addCode("}\n");
    }

    public MethodSpec generateOneToOneOwning(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        MethodSpec.Builder m = MethodSpec.methodBuilder("oneToOneOwning")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(e)
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "fkValue")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() != RelationshipKind.ONE_TO_ONE || !rel.owning()) continue;

            m.addCode("case $S: {\n", rel.fieldName());
            m.addStatement("if (fkValue == null) return null");
            m.addStatement("$T field = repositoryModel.fieldByName($S)",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel"), rel.fieldName());

            m.beginControlFlow(
                "if (field.consistency() == io.github.flameyossnowy.universal.api.annotations.enums.Consistency.STRONG)");
            m.addStatement("return ($T) handler.handleOneToOneRelationshipOwning(fkValue, field)", e);
            m.endControlFlow();

            m.addStatement("$T<$T, $T> inner = oneToOneCache.computeIfAbsent(fkValue, f -> new $T<>())",
                Map.class, String.class, Object.class, ConcurrentHashMap.class);
            m.addStatement("$T cached = inner.get(fieldName)", Object.class);
            m.beginControlFlow("if (cached != null)");
            m.addStatement("return ($T) (cached == $L ? null : cached)", e, NULL_SENTINEL_FIELD);
            m.endControlFlow();

            m.addStatement("$T loaded = handler.handleOneToOneRelationshipOwning(fkValue, field)", Object.class);
            m.addStatement("$T stored = (loaded == null) ? $L : loaded", Object.class, NULL_SENTINEL_FIELD);
            m.addStatement("$T existing = inner.putIfAbsent(fieldName, stored)", Object.class);
            m.addStatement("return ($T) (existing != null ? (existing == $L ? null : existing) : loaded)",
                e, NULL_SENTINEL_FIELD);

            m.addCode("}\n");
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown owning OneToOne field: ");
        m.endControlFlow();
        return m.build();
    }
}