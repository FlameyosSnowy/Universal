package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.checker.FieldModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single Responsibility: generate the four collection-loader methods
 * ({@code loadList}, {@code loadSet}, {@code loadMap}, {@code loadArray})
 * with their per-entity, per-field caching logic.
 */
public final class CollectionLoaderMethodGenerator {

    public CollectionLoaderMethodGenerator() {}

    // ------------------------------------------------------------------

    public MethodSpec generateLoadList(RepositoryModel repo, TypeName idType) {
        TypeVariableName e  = TypeVariableName.get("E");
        ClassName collection = ClassName.get(Collection.class);

        MethodSpec.Builder m = collectionLoaderBase("loadList", e, collection, idType);

        m.beginControlFlow("switch (fieldName)");
        for (FieldModel field : repo.fields()) {
            if (!field.relationship() && GeneratorUtils.isListType(field.type())) {
                emitCollectionCase(m, field, e, collection, "listCache", "LIST");
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown List field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateLoadSet(RepositoryModel repo, TypeName idType) {
        TypeVariableName e  = TypeVariableName.get("E");
        ClassName collection = ClassName.get(Collection.class);

        MethodSpec.Builder m = collectionLoaderBase("loadSet", e, collection, idType);

        m.beginControlFlow("switch (fieldName)");
        for (FieldModel field : repo.fields()) {
            if (!field.relationship() && GeneratorUtils.isSetType(field.type())) {
                emitCollectionCase(m, field, e, collection, "setCache", "SET");
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown Set field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateLoadMap(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        TypeVariableName k = TypeVariableName.get("K");
        ClassName map       = ClassName.get(Map.class);

        MethodSpec.Builder m = MethodSpec.methodBuilder("loadMap")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .addTypeVariable(k)
            .returns(ParameterizedTypeName.get(map, e, k))
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "keyType")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), k), "valueType");

        m.beginControlFlow("switch (fieldName)");
        for (FieldModel field : repo.fields()) {
            if (!field.relationship() && GeneratorUtils.isMapType(field.type())) {
                m.addStatement(
                    "case $S: return ($T) mapCache"
                    + ".computeIfAbsent(id, k2 -> new $T<>())"
                    + ".computeIfAbsent(fieldName, fn -> ($T) collectionHandler.fetchMap(id, fn, keyType, valueType, repositoryModel))",
                    field.name(),
                    ParameterizedTypeName.get(map, e, k),
                    ConcurrentHashMap.class,
                    ParameterizedTypeName.get(Map.class, Object.class, Object.class));
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown Map field: ");
        m.endControlFlow();
        return m.build();
    }

    public MethodSpec generateLoadArray(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");

        MethodSpec.Builder m = MethodSpec.methodBuilder("loadArray")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(ArrayTypeName.of(e))
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "componentType");

        m.beginControlFlow("switch (fieldName)");
        for (FieldModel field : repo.fields()) {
            if (!field.relationship() && field.type().getKind() == TypeKind.ARRAY) {
                m.addStatement(
                    "case $S: return ($T) arrayCache"
                    + ".computeIfAbsent(id, k -> new $T<>())"
                    + ".computeIfAbsent(fieldName, fn -> collectionHandler.fetchArray(id, fn, componentType, repositoryModel))",
                    field.name(),
                    ArrayTypeName.of(e),
                    ConcurrentHashMap.class);
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class, "Unknown Array field: ");
        m.endControlFlow();
        return m.build();
    }

    // ------------------------------------------------------------------ helpers

    private static MethodSpec.Builder collectionLoaderBase(String methodName,
                                                            TypeVariableName e,
                                                            ClassName collection,
                                                            TypeName idType) {
        return MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(ParameterizedTypeName.get(collection, e))
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");
    }

    private static void emitCollectionCase(MethodSpec.Builder m, FieldModel field,
                                            TypeVariableName e, ClassName collection,
                                            String cacheField, String kindName) {
        m.addStatement(
            "case $S: return ($T) $L"
            + ".computeIfAbsent(id, k -> new $T<>())"
            + ".computeIfAbsent(fieldName, fn -> ($T) collectionHandler"
            + ".fetchCollection(id, fn, elementType, $T.$L, repositoryModel))",
            field.name(),
            ParameterizedTypeName.get(collection, e),
            cacheField,
            ClassName.get(ConcurrentHashMap.class),
            ParameterizedTypeName.get(Collection.class, Object.class),
            ClassName.get(io.github.flameyossnowy.universal.api.factory.CollectionKind.class),
            kindName);
    }
}
