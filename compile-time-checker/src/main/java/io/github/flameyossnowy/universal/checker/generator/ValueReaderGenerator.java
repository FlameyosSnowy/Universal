package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;
import io.github.flameyossnowy.universal.api.GeneratedRepositoryFactory;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.checker.FieldModel;
import io.github.flameyossnowy.universal.checker.GeneratorUtils;
import io.github.flameyossnowy.universal.checker.RelationshipModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;
import io.github.flameyossnowy.universal.checker.processor.AnnotationUtils;
import io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import java.util.List;

import static io.github.flameyossnowy.universal.checker.GeneratorUtils.write;


public class ValueReaderGenerator {
    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public ValueReaderGenerator(Types types, Elements elements, Filer filer) {
        this.types = types;
        this.elements = elements;
        this.filer = filer;
    }

    public void generateValueReader(RepositoryModel repo, List<String> qualifiedNames) {
        String className = repo.entitySimpleName() + "_ValueReader";

        // Check if entity has a primary key
        boolean hasId = false;
        for (FieldModel fieldModel : repo.fields()) {
            if (fieldModel.id()) {
                hasId = true;
                break;
            }
        }

        ParameterizedTypeName readerInterface = ParameterizedTypeName.get(
            ClassName.get("io.github.flameyossnowy.universal.api.factory", "ValueReader"),
            TypeVariableName.get("ID")
        );
        ClassName dbResult = ClassName.get("io.github.flameyossnowy.universal.api.result", "DatabaseResult");
        ClassName typeResolverRegistry = ClassName.get("io.github.flameyossnowy.universal.api.resolver", "TypeResolverRegistry");
        ClassName jsonCodec = ClassName.get(JsonCodec.class);
        ClassName repositoryModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RepositoryModel");
        ParameterizedTypeName repoModelType = ParameterizedTypeName.get(repositoryModel, WildcardTypeName.subtypeOf(TypeName.OBJECT), TypeVariableName.get("ID"));

        // Handle null ID type - use Void as placeholder for entities without IDs
        TypeName idTypeName;
        if (repo.idType() != null) {
            idTypeName = TypeName.get(repo.idType()).box();
        } else {
            idTypeName = ClassName.get(Void.class);
        }

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.generator.UnifiedFactoryGenerator")
                .build())
            .addTypeVariable(TypeVariableName.get("ID"))
            .addSuperinterface(readerInterface)
            .addSuperinterface(TypeName.get(GeneratedRepositoryFactory.class));

        // Generate register() method
        builder.addMethod(MethodSpec.methodBuilder("register")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement(
                "io.github.flameyossnowy.universal.api.meta.GeneratedValueReaders.<$T>register($S, (result, registry, id, model) -> new $L(result, registry, id, model))",
                idTypeName,
                repo.tableName(),
                repo.entitySimpleName() + "_ValueReader"
            )
            .build());

        builder.addField(FieldSpec.builder(dbResult, "result", Modifier.PRIVATE).build());
        builder.addField(FieldSpec.builder(typeResolverRegistry, "registry", Modifier.PRIVATE).build());
        builder.addField(FieldSpec.builder(TypeVariableName.get("ID"), "id", Modifier.PRIVATE).build());
        builder.addField(FieldSpec.builder(repoModelType, "model", Modifier.PRIVATE).build());

        CodeBlock.Builder columnNamesInit = CodeBlock.builder().add("{\n");
        for (FieldModel field : repo.fields()) {
            if (!field.participatesInConstruction()) continue;
            if (!field.isJson() && !field.isNotCollection(types, elements)) continue;
            columnNamesInit.add("  $S,\n", field.columnName());
        }
        columnNamesInit.add("}");

        builder.addField(FieldSpec.builder(
                ArrayTypeName.of(String.class),
                "COLUMN_NAMES",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(columnNamesInit.build())
            .build());

        builder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(dbResult, "result")
            .addParameter(typeResolverRegistry, "registry")
            .addParameter(TypeVariableName.get("ID"), "id")
            .addParameter(repoModelType, "model")
            .addStatement("this.result = result")
            .addStatement("this.registry = registry")
            .addStatement("this.id = id")
            .addStatement("this.model = model")
            .build());

        // for ServiceLoader
        builder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .build());

        MethodSpec getDatabaseResultMethod = MethodSpec.methodBuilder("getDatabaseResult")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(DatabaseResult.class)
            .addStatement("return this.result")
            .build();

        builder.addMethod(getDatabaseResultMethod);

        builder.addMethod(MethodSpec.methodBuilder("getId")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeVariableName.get("ID"))
            .addStatement("return id")
            .build());

        builder.addMethod(MethodSpec.methodBuilder("getRepositoryModel")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(repositoryModel, WildcardTypeName.subtypeOf(TypeName.OBJECT), WildcardTypeName.subtypeOf(TypeName.OBJECT)))
            .addStatement("return model")
            .build());

        MethodSpec.Builder readMethod = MethodSpec.methodBuilder("read")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(TypeVariableName.get("T"))
            .returns(TypeVariableName.get("T"))
            .addParameter(int.class, "index")
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "unchecked")
                .build());

        readMethod.beginControlFlow("switch (index)");
        int index = 0;
        int helperMethodIndex = 1;

        for (FieldModel field : repo.fields()) {
            if (!field.participatesInConstruction()) {
                // Include owning-side OneToOne/ManyToOne FK columns as raw reads
                if (field.relationship()) {
                    RelationshipModel rel = repo.relationships().stream()
                        .filter(r -> r.hasField(field))
                        .findFirst().orElse(null);
                    if (rel != null && rel.owning() && rel.relationshipKind() != RelationshipKind.ONE_TO_MANY) {
                        TypeMirror idType = getTargetPrimaryKeyType(rel); // UUID, Long, etc.
                        // We read the raw FK column value (e.g. warp VARCHAR(36) -> UUID)
                        TypeName typeName = ClassName.get(types.erasure(idType));
                        readMethod.addStatement("case $L: return (T) registry.resolve($T.class).resolve(result, $S)",
                            index, typeName, field.columnName());
                        index++;
                    }
                }
                continue;
            }

            String columnName = field.columnName();
            TypeMirror fieldType = field.type();

            boolean needsSpecialHandling = !field.relationship() && !field.isJson() && needsSpecialLoader(fieldType);

            if (needsSpecialHandling) {
                readMethod.addStatement("case $L: return (T) read$L($S)", index, helperMethodIndex, columnName);
                helperMethodIndex++;
            } else {
                TypeMirror rawType = getRawType(fieldType);

                // JSON fields: deserialize via JsonCodec instead of TypeResolver
                if (field.isJson() && !field.relationship()) {
                    TypeName typeName = ClassName.get(rawType);
                    ClassName codecClass = ClassName.bestGuess(field.jsonCodecClass());

                    readMethod.beginControlFlow("case $L:", index);
                    readMethod.addCode(CodeBlock.builder()
                        .beginControlFlow("try")
                        .addStatement("Object __raw = result.get($S, Object.class)", columnName)
                        .addStatement("String __json = __raw == null ? null : __raw.toString()")
                        .beginControlFlow("if (__json == null)")
                        .addStatement("return null")
                        .endControlFlow()
                        .addStatement("$T __field = model.fieldByName($S)",
                            ParameterizedTypeName.get(ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel"), WildcardTypeName.subtypeOf(TypeName.OBJECT)),
                            field.name())
                        .addStatement("$T __adapter = registry.getJsonAdapter()",
                            ClassName.get("io.github.flameyossnowy.uniform.json", "JsonAdapter"))
                        .addStatement("$T __codec = registry.getJsonCodecFromSupplier($T.class, __field.jsonCodecSupplier(), __adapter)",
                            ParameterizedTypeName.get(jsonCodec, WildcardTypeName.subtypeOf(TypeName.OBJECT)),
                            codecClass)
                        .addStatement("return (T) (($T) __codec).deserialize(__json, (Class) $T.class)",
                            jsonCodec,
                            typeName)
                        .nextControlFlow("catch ($T e)", Exception.class)
                        .addStatement("throw new $T($S + e.getMessage(), e)", RuntimeException.class, "Failed to deserialize JSON field '" + columnName + "': ")
                        .endControlFlow()
                        .build());
                    readMethod.endControlFlow();

                    index++;
                    continue;
                }

                // Handle ID field
                if (field.id()) {
                    if (hasId) {
                        readMethod.beginControlFlow("case $L:", index);
                        readMethod.addStatement("if (id != null) return (T) id");

                        // Fallback: try to read from result
                        TypeName typeName = ClassName.get(rawType);
                        readMethod.addCode(CodeBlock.builder()
                            .beginControlFlow("try")
                            .addStatement("return (T) registry.resolve($T.class).resolve(result, $S)", typeName, columnName)
                            .nextControlFlow("catch ($T e)", Exception.class)
                            .addStatement("return null")
                            .endControlFlow()
                            .build());
                        readMethod.endControlFlow();
                    } else {
                        TypeName typeName = ClassName.get(rawType);
                        readMethod.addStatement("case $L: return (T) registry.resolve($T.class).resolve(result, $S)", index, typeName, columnName);
                    }
                } else {
                    // Regular field
                    TypeName typeName = ClassName.get(rawType);
                    readMethod.addStatement("case $L: return (T) registry.resolve($T.class).resolve(result, $S)", index, typeName, columnName);
                }
            }
            index++;
        }

        readMethod.addStatement("default: throw new $T($S + index)", IndexOutOfBoundsException.class, "Invalid column index: ");
        readMethod.endControlFlow();
        builder.addMethod(readMethod.build());

        helperMethodIndex = 1;
        for (FieldModel field : repo.fields()) {
            if (!field.participatesInConstruction()) continue;

            if (!field.relationship() && !field.isJson() && needsSpecialLoader(field.type())) {
                String methodName = "read" + helperMethodIndex;
                generateValueReaderMethod(builder, field, methodName, "result.repositoryModel()");
                helperMethodIndex++;
            }
        }

        qualifiedNames.add(repo.packageName() + (repo.packageName() != null && !repo.packageName().isEmpty() ? "." : "") + className);
        write(repo.packageName(), builder.build(), filer);
    }

    private TypeMirror getTargetPrimaryKeyType(RelationshipModel rel) {
        TypeMirror targetType = rel.targetType();
        if (!(targetType instanceof DeclaredType dt)) return null;

        TypeElement targetElement = (TypeElement) dt.asElement();

        for (Element enclosed : targetElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            if (AnnotationUtils.hasAnnotation(enclosed, Id.class.getCanonicalName())) {
                return enclosed.asType();
            }
        }

        return null;
    }

    public TypeMirror getRawType(TypeMirror type) {
        if (type instanceof DeclaredType) {
            return types.erasure(type);
        }
        return type;
    }

    /**
     * Returns the raw ClassName for use with .class literals.
     * Generic types like Map<String, String> can't use .class directly.
     */
    private TypeName rawClassForLiteral(TypeMirror type) {
        if (type == null) return TypeName.OBJECT;
        // For declared types (classes/interfaces), get the raw type without generics
        if (type instanceof DeclaredType dt) {
            String qualifiedName = dt.asElement().toString();
            return ClassName.bestGuess(qualifiedName);
        }
        return TypeName.get(type);
    }

    /**
     * Determines CollectionKind for multimap value collections.
     * Returns LIST for List values, SET for Set values, etc.
     */
    private String getCollectionKindForType(TypeMirror type) {
        if (type == null) return "LIST";
        String typeStr = type.toString();
        if (typeStr.startsWith("java.util.Set")) return "SET";
        if (typeStr.startsWith("java.util.List")) return "LIST";
        if (typeStr.startsWith("java.util.Collection")) return "LIST";
        // Default to LIST for other collection types
        return "LIST";
    }

    public boolean needsSpecialLoader(TypeMirror type) {
        return TypeMirrorUtils.isArray(type)
            || TypeMirrorUtils.isMap(types, elements, type)
            || TypeMirrorUtils.isList(types, elements, type)
            || TypeMirrorUtils.isSet(types, elements, type);
    }

    public void generateValueReaderMethod(
        TypeSpec.Builder builder,
        FieldModel field,
        String methodName,
        String repoModelRef
    ) {
        TypeMirror rawType = field.type();
        TypeName returnType;
        if (TypeMirrorUtils.isArray(rawType)) {
            returnType = ArrayTypeName.get(field.type());
        } else {
            returnType = TypeName.get(rawType);
        }

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PRIVATE)
            .returns(returnType)
            .addParameter(String.class, "columnName")
            .beginControlFlow("try");

        // Check if ID is null and handle gracefully for collections/arrays
        boolean needsId = TypeMirrorUtils.isArray(rawType)
            || TypeMirrorUtils.isMap(types, elements, rawType)
            || TypeMirrorUtils.isCollection(types, elements, rawType);

        if (needsId) {
            methodBuilder.beginControlFlow("if (id == null)");
            methodBuilder.addStatement("return null");
            methodBuilder.endControlFlow();
        }

        if (TypeMirrorUtils.isArray(rawType)) {
            // Arrays: if the DB supports them natively, read straight from the result set;
            // otherwise delegate to the collection handler (separate table / join).
            TypeName typeName = TypeName.get(rawType);
            if (!(typeName instanceof ArrayTypeName arrayTypeName)) {
                throw new IllegalStateException("Expected ArrayTypeName for array field: " + field.name());
            }
            TypeName component = arrayTypeName.componentType;

            ParameterizedTypeName repoModelType = ParameterizedTypeName.get(
                ClassName.get(io.github.flameyossnowy.universal.api.meta.RepositoryModel.class),
                WildcardTypeName.subtypeOf(TypeName.OBJECT),
                TypeVariableName.get("ID")
            );

            methodBuilder
                .beginControlFlow("if (result.supportsArraysNatively())")
                // Native path: the array column is in the current ResultSet row.
                // We reuse the TypeResolverRegistry so adapters (e.g. JDBC Array -> T[])
                // are applied consistently.
                .addStatement("return ($T) result.get(columnName, java.sql.Array.class).getArray()", returnType)
                .nextControlFlow("else")
                // Non-native path: array is stored in a related table; fetch via handler.
                .addStatement("return result.getCollectionHandler().fetchArray(id, columnName, $T.class, ($T) $L)",
                    component, repoModelType, repoModelRef)
                .endControlFlow();

        } else if (TypeMirrorUtils.isMap(types, elements, rawType)) {
            boolean isMultiMap = TypeMirrorUtils.isCollection(types, elements, field.mapValueType());

            ParameterizedTypeName repoModelType = ParameterizedTypeName.get(
                ClassName.get(io.github.flameyossnowy.universal.api.meta.RepositoryModel.class),
                WildcardTypeName.subtypeOf(TypeName.OBJECT),
                TypeVariableName.get("ID")
            );

            if (isMultiMap) {
                // For multimaps: Map<K, Collection<V>>
                // - keyType = K (from mapKeyType)
                // - elementType = V (from value collection's element type)
                // - collectionKind = LIST/SET based on value collection type
                String collectionKind = getCollectionKindForType(field.mapValueType());
                // Extract element type from the value collection (e.g., String from List<String>)
                TypeMirror collectionElementType = GeneratorUtils.genericArg(field.mapValueType(), 0);
                methodBuilder.addStatement(
                    "return result.getCollectionHandler().fetchMultiMap(id, columnName, $T.class, $T.class, $T.$L, ($T) $L)",
                    rawClassForLiteral(field.mapKeyType()),
                    rawClassForLiteral(collectionElementType),
                    ClassName.get("io.github.flameyossnowy.universal.api.factory", "CollectionKind"),
                    collectionKind,
                    repoModelType,
                    repoModelRef
                );
            } else {
                methodBuilder.addStatement(
                    "return result.getCollectionHandler().fetchMap(id, columnName, $T.class, $T.class, ($T) $L)",
                    rawClassForLiteral(field.mapKeyType()),
                    rawClassForLiteral(field.mapValueType()),
                    repoModelType,
                    repoModelRef
                );
            }

        } else if (TypeMirrorUtils.isCollection(types, elements, rawType)) {
            ParameterizedTypeName repoModelType = ParameterizedTypeName.get(
                ClassName.get(io.github.flameyossnowy.universal.api.meta.RepositoryModel.class),
                WildcardTypeName.subtypeOf(TypeName.OBJECT),
                TypeVariableName.get("ID")
            );

            methodBuilder.addStatement(
                "return result.getCollectionHandler().fetchCollection(id, columnName, $T.class, $T.$L, ($T) $L)",
                rawClassForLiteral(field.elementType()),
                ClassName.get("io.github.flameyossnowy.universal.api.factory", "CollectionKind"),
                field.collectionKind().name(),
                repoModelType,
                repoModelRef
            );

        } else {
            throw new IllegalStateException("Unsupported collection field: " + field.name());
        }

        methodBuilder
            .nextControlFlow("catch ($T e)", Exception.class)
            .addStatement("throw new $T($S + $S, e)", RuntimeException.class, "Failed to load field ", field.name())
            .endControlFlow();

        builder.addMethod(methodBuilder.build());
    }
}