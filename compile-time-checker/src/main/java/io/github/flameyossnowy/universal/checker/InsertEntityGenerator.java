package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.generator.RepositoryFieldModelGenerator;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.*;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Single Responsibility: generate the {@code insertEntity} method body.
 *
 * <p>Array-typed fields are handled conditionally:
 * <ul>
 *   <li>When {@code params.supportsArraysNatively()} is {@code true} the value is
 *       bound directly via {@code stmt.set(…)}.</li>
 *   <li>Otherwise the array is skipped here and delegated to
 *       {@link InsertCollectionEntitiesGenerator}.</li>
 * </ul>
 */
public final class InsertEntityGenerator {

    private final Types types;
    private final Elements elements;
    private final Messager messager;

    public InsertEntityGenerator(Types types, Elements elements, Messager messager) {
        this.types    = types;
        this.elements = elements;
        this.messager = messager;
    }

    // ------------------------------------------------------------------

    public MethodSpec generate(RepositoryModel repo, ClassName entityType) {
        ClassName dbParams   = ClassName.get("io.github.flameyossnowy.universal.api.params", "DatabaseParameters");
        ClassName repoMeta   = ClassName.get("io.github.flameyossnowy.universal.api.meta",   "RepositoryModel");
        ClassName genMeta    = ClassName.get("io.github.flameyossnowy.universal.api.meta",   "GeneratedMetadata");
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta",   "FieldModel");

        TypeMirror thisIdMirror = repo.primaryKeys().isEmpty()
            ? null : repo.primaryKeys().getFirst().type();
        TypeName thisIdName       = thisIdMirror == null ? TypeName.get(Object.class) : TypeName.get(thisIdMirror);
        TypeName thisIdNameBoxed  = thisIdName.isPrimitive() ? thisIdName.box() : thisIdName;

        MethodSpec.Builder m = MethodSpec.methodBuilder("insertEntity")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addException(Exception.class)
            .addParameter(dbParams,    "stmt")
            .addParameter(entityType,  "entity");

        for (FieldModel field : repo.fields()) {
            if (field.autoIncrement()) continue;
            if (!field.insertable())   continue;

            TypeMirror fieldMirror = field.type();
            String     fieldName   = field.columnName();

            if (GeneratorUtils.isCollectionOrMapType(types, elements, fieldMirror)) {
                continue;
            }

            String   valueVar  = "value_" + fieldName;
            TypeName fieldType = RepositoryFieldModelGenerator.getTypeName(fieldMirror);

            m.addStatement("$T $L = entity.$L()", fieldType, valueVar, field.getterName());

            // ---- @Now timestamp injection -----------------------------------
            emitNowTimestamp(m, field, fieldMirror, valueVar);

            // ---- Relationship FK binding ------------------------------------
            if (field.relationship() && field.relationshipKind() != null) {
                RelationshipKind kind = field.relationshipKind();
                if (kind == RelationshipKind.ONE_TO_ONE || kind == RelationshipKind.MANY_TO_ONE) {
                    emitRelationshipFk(m, repo, field, fieldName, valueVar,
                        thisIdMirror, thisIdNameBoxed, repoMeta, genMeta, fieldModel, entityType);
                }
                // ONE_TO_MANY → collection, already filtered above
                continue;
            }

            // ---- Arrays: native vs. deferred --------------------------------
            if (fieldMirror.getKind() == TypeKind.ARRAY) {
                m.beginControlFlow("if (stmt.supportsArraysNatively())")
                    .addStatement("stmt.set($S, $L, $T.class)", fieldName, valueVar, fieldType)
                    .endControlFlow();
                continue;
            }

            // ---- Scalar field -----------------------------------------------
            m.addStatement("stmt.set($S, $L, $T.class)", fieldName, valueVar, fieldType);
        }

        return m.build();
    }

    // ------------------------------------------------------------------ helpers

    private static void emitNowTimestamp(MethodSpec.Builder m, FieldModel field,
                                          TypeMirror fieldMirror, String valueVar) {
        if (!field.hasNowAnnotation()) return;
        String typeName = fieldMirror.toString();
        if      (typeName.contains("java.time.Instant"))         m.addStatement("$L = $T.now()", valueVar, ClassName.get("java.time", "Instant"));
        else if (typeName.contains("java.time.LocalDateTime"))   m.addStatement("$L = $T.now()", valueVar, ClassName.get("java.time", "LocalDateTime"));
        else if (typeName.contains("java.time.LocalDate"))       m.addStatement("$L = $T.now()", valueVar, ClassName.get("java.time", "LocalDate"));
        else if (typeName.contains("java.time.ZonedDateTime"))   m.addStatement("$L = $T.now()", valueVar, ClassName.get("java.time", "ZonedDateTime"));
        else if (typeName.contains("java.time.OffsetDateTime"))  m.addStatement("$L = $T.now()", valueVar, ClassName.get("java.time", "OffsetDateTime"));
        else if (typeName.contains("java.sql.Timestamp"))        m.addStatement("$L = new $T($T.currentTimeMillis())", valueVar, ClassName.get("java.sql", "Timestamp"), System.class);
        else if (typeName.contains("java.util.Date"))            m.addStatement("$L = new $T()", valueVar, ClassName.get("java.util", "Date"));
        m.addStatement("entity.$L($L)", field.setterName(), valueVar);
    }

    private void emitRelationshipFk(MethodSpec.Builder m, RepositoryModel repo,
                                     FieldModel field, String fieldName, String valueVar,
                                     TypeMirror thisIdMirror, TypeName thisIdNameBoxed,
                                     ClassName repoMeta, ClassName genMeta, ClassName fieldModel,
                                     ClassName entityType) {
        m.beginControlFlow("if ($L != null)", valueVar);

        ClassName relatedEntityClass = ClassName.bestGuess(field.typeQualifiedName());
        IdFieldInfo relatedId = resolveIdField(field.typeQualifiedName());

        if (relatedId == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Related entity '" + field.typeQualifiedName() + "' has no @Id field");
            m.addStatement("throw new $T($S)", IllegalArgumentException.class,
                "Related entity has no @Id field for relationship field: " + fieldName);
        } else {
            if (thisIdMirror != null) {
                TypeMirror relatedIdType = normalizeId(relatedId.type());
                TypeMirror thisIdType    = normalizeId(thisIdMirror);
                if (!types.isSameType(thisIdType, relatedIdType)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "Relationship '" + repo.entityQualifiedName() + "." + fieldName
                        + "' points to entity '" + field.typeQualifiedName()
                        + "' with a different @Id type. Expected: " + thisIdType
                        + ", got: " + relatedIdType);
                }
            }

            TypeName relatedRepoType      = ParameterizedTypeName.get(repoMeta, relatedEntityClass, thisIdNameBoxed);
            TypeName relatedFieldModelType = ParameterizedTypeName.get(fieldModel, relatedEntityClass);

            m.addStatement("$T relatedRepo = $T.getByEntityClass($T.class)", relatedRepoType, genMeta, relatedEntityClass);
            m.beginControlFlow("if (relatedRepo != null)");
            m.addStatement("$T pkField = relatedRepo.fieldByName($S)", relatedFieldModelType, relatedId.name());
            m.beginControlFlow("if (pkField != null)");
            m.addStatement("Object pkValue = pkField.getValue($L)", valueVar);
            m.addStatement("stmt.set($S, pkValue, pkField.type())", field.columnName());
            m.nextControlFlow("else");
            m.addStatement("throw new $T($S + $S)", IllegalArgumentException.class,
                "Primary key not found for relationship field: ", fieldName);
            m.endControlFlow();
            m.nextControlFlow("else");
            m.addStatement("throw new $T($S + $S)", IllegalArgumentException.class,
                "Repository metadata not found for: ", field.typeQualifiedName());
            m.endControlFlow();
        }

        m.nextControlFlow("else");
        m.addStatement("stmt.set($S, null, $T.class)", fieldName, thisIdNameBoxed);
        m.endControlFlow();
    }

    private IdFieldInfo resolveIdField(String entityQualifiedName) {
        var entity = elements.getTypeElement(entityQualifiedName);
        if (entity == null) return null;
        for (Element e : entity.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) continue;
            if (!io.github.flameyossnowy.universal.checker.processor.AnnotationUtils
                    .hasAnnotation(e, io.github.flameyossnowy.universal.api.annotations.Id.class.getCanonicalName()))
                continue;
            var ve = (VariableElement) e;
            return new IdFieldInfo(ve.getSimpleName().toString(), ve.asType());
        }
        return null;
    }

    private TypeMirror normalizeId(TypeMirror type) {
        if (type == null) return null;
        if (type.getKind().isPrimitive()) return types.boxedClass((PrimitiveType) type).asType();
        return type;
    }

    /** Minimal DTO – mirrors the private record in the original class. */
    public record IdFieldInfo(String name, TypeMirror type) {}
}
