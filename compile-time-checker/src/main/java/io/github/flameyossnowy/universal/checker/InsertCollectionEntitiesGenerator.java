package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.checker.FieldModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single Responsibility: generate the {@code insertCollectionEntities} method body.
 *
 * <p>Array fields call {@code handler.insertArray(…)} only when
 * {@code !params.supportsArraysNatively()} — the complementary branch is
 * emitted by {@link InsertEntityGenerator} inside {@code insertEntity}.
 */
public final class InsertCollectionEntitiesGenerator {

    public InsertCollectionEntitiesGenerator() {}

    // ------------------------------------------------------------------

    public MethodSpec generate(RepositoryModel repo, ClassName entityType, TypeName idType) {
        ClassName dbParams         = ClassName.get("io.github.flameyossnowy.universal.api.params",   "DatabaseParameters");
        ClassName collectionHandler = ClassName.get("io.github.flameyossnowy.universal.api.handler", "CollectionHandler");

        MethodSpec.Builder m = MethodSpec.methodBuilder("insertCollectionEntities")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addException(Exception.class)
            .addParameter(entityType,    "entity")
            .addParameter(idType.box(),  "id")
            .addParameter(dbParams,      "params");

        m.addStatement("$T handler = params.getCollectionHandler()", collectionHandler);

        boolean hasCollections = false;

        for (FieldModel field : repo.fields()) {
            if (field.relationship()) continue;

            TypeMirror fieldType  = field.type();
            String     typeName   = fieldType.toString();
            String     fieldName  = field.name();
            String     getterCall = "entity." + field.getterName() + "()";

            // ---- List / Set / Collection ---------------------------------
            if (typeName.startsWith("java.util.List")
                || typeName.startsWith("java.util.Set")
                || typeName.startsWith("java.util.Collection")) {

                hasCollections = true;
                TypeMirror elementMirror = GeneratorUtils.genericArg(fieldType, 0);
                ClassName  elementClass  = ClassName.bestGuess(
                    elementMirror != null ? elementMirror.toString() : "Object");

                m.addStatement(
                    "handler.insertCollection(id, $S, $L, $T.class, this.repositoryModel)",
                    fieldName, getterCall, elementClass);
                continue;
            }

            // ---- Map ----------------------------------------------------
            if (typeName.startsWith("java.util.Map")) {
                hasCollections = true;
                TypeMirror keyMirror   = GeneratorUtils.genericArg(fieldType, 0);
                TypeMirror valueMirror = GeneratorUtils.genericArg(fieldType, 1);
                ClassName  keyClass    = ClassName.bestGuess(keyMirror   != null ? keyMirror.toString()   : "Object");
                ClassName  valueClass  = ClassName.bestGuess(valueMirror != null ? valueMirror.toString() : "Object");

                if (GeneratorUtils.isMultiMap(valueMirror)) {
                    TypeMirror colValueMirror = GeneratorUtils.genericArg(valueMirror, 0);
                    ClassName  colValueClass  = ClassName.bestGuess(
                        colValueMirror != null ? colValueMirror.toString() : "Object");
                    m.addStatement(
                        "handler.insertMultiMap(id, $S, $L, $T.class, $T.class, this.repositoryModel)",
                        fieldName, getterCall, keyClass, colValueClass);
                } else {
                    m.addStatement(
                        "handler.insertMap(id, $S, $L, $T.class, $T.class, this.repositoryModel)",
                        fieldName, getterCall, keyClass, valueClass);
                }
                continue;
            }

            // ---- Array: only when the DB does NOT support arrays natively ---
            if (fieldType.getKind() == TypeKind.ARRAY) {
                hasCollections = true;
                ArrayType  arrayType      = (ArrayType) fieldType;
                TypeMirror componentType  = arrayType.getComponentType();
                ClassName  componentClass = ClassName.bestGuess(componentType.toString());

                m.beginControlFlow("if (!params.supportsArraysNatively())")
                    .addStatement(
                        "handler.insertArray(id, $S, $L, $T.class, this.repositoryModel)",
                        fieldName, getterCall, componentClass)
                    .endControlFlow();
            }
        }

        if (!hasCollections) {
            m.addComment("No collection/array fields to insert");
        }

        return m.build();
    }
}
