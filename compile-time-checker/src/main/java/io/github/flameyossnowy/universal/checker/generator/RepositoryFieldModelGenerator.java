package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.meta.JsonIndexModel;
import io.github.flameyossnowy.universal.api.meta.JsonStorageKind;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.FieldModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

import static io.github.flameyossnowy.universal.checker.UnifiedFactoryGenerator.literal;

public class RepositoryFieldModelGenerator {
    public static void generateFieldModels(TypeSpec.Builder type, RepositoryModel repo, ClassName entityClass) {
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        TypeName fieldModelType = ParameterizedTypeName.get(fieldModel, entityClass);

        // Generate individual field model classes
        for (FieldModel f : repo.fields()) {
            TypeSpec fieldModelClass = generateFieldModelClass(f, entityClass);
            type.addType(fieldModelClass);
        }

        // Generate FIELDS array with instances
        CodeBlock.Builder arrInit = CodeBlock.builder().add("{\n");
        for (FieldModel f : repo.fields()) {
            String fieldModelClassName = entityClass.simpleName() + "_" + f.name() + "_FieldModel";
            arrInit.add("  new $L(),\n", fieldModelClassName);
        }
        arrInit.add("}");

        type.addField(FieldSpec.builder(
                ArrayTypeName.of(fieldModel),
                "FIELDS",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(arrInit.build())
            .build());

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(List.class), fieldModelType),
                "FIELDS_VIEW",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(
                "$T.stream(FIELDS).map(f -> ($T) f).toList()",
                ClassName.get("java.util", "Arrays"),
                fieldModelType
            )
            .build());

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(String.class)),
                "PRIMARY_KEYS",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(
                "$T.stream(FIELDS).filter(FieldModel::id).map(f -> f.name()).toList()",
                ClassName.get("java.util", "Arrays")
            )
            .build());
    }

    public static TypeSpec generateFieldModelClass(
        FieldModel field,
        ClassName entityClass
    ) {
        String className = entityClass.simpleName() + "_" + field.name() + "_FieldModel";
        ClassName fieldModelInterface = ClassName.get(
            "io.github.flameyossnowy.universal.api.meta",
            "FieldModel"
        );
        TypeName fieldModelType = ParameterizedTypeName.get(fieldModelInterface, entityClass);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(fieldModelType);

        classBuilder.addMethod(createStringMethod("name", field.name()));
        classBuilder.addMethod(createStringMethod("columnName", field.columnName()));
        classBuilder.addMethod(createTypeMethod(field.type()));
        classBuilder.addMethod(createBooleanMethod("id", field.id()));
        classBuilder.addMethod(createBooleanMethod("autoIncrement", field.autoIncrement()));
        classBuilder.addMethod(createBooleanMethod("nullable", field.nullable()));
        classBuilder.addMethod(createBooleanMethod("relationship", field.relationship()));
        classBuilder.addMethod(createRelationshipKindMethod(field.relationshipKind()));
        classBuilder.addMethod(createConsistencyMethod(field.consistency()));
        classBuilder.addMethod(createStringMethod("getterName", field.getterName()));
        classBuilder.addMethod(createStringMethod("setterName", field.setterName()));
        classBuilder.addMethod(createBooleanMethod("lazy", field.lazy()));
        classBuilder.addMethod(createBooleanMethod("insertable", field.insertable()));
        classBuilder.addMethod(createBooleanMethod("updatable", field.updatable()));

        classBuilder.addMethod(createBooleanMethod("hasNowAnnotation", field.hasNowAnnotation()));
        classBuilder.addMethod(createBooleanMethod("hasBinaryAnnotation", field.hasBinaryAnnotation()));
        classBuilder.addMethod(createBooleanMethod("hasUniqueAnnotation", field.hasUniqueAnnotation()));
        classBuilder.addMethod(createNullableStringMethod("defaultValue", field.defaultValue()));
        classBuilder.addMethod(createDefaultValueProviderMethod(field.defaultValueProviderClass()));
        classBuilder.addMethod(createBooleanMethod("enumAsOrdinal", field.enumAsOrdinal()));
        classBuilder.addMethod(createNullableStringMethod("externalRepository", field.externalRepository()));

        classBuilder.addMethod(generateGetMethod(field, entityClass));
        classBuilder.addMethod(generateSetMethod(field, entityClass));

        classBuilder.addMethod(createConditionMethod(field.condition()));
        classBuilder.addMethod(createOnDeleteMethod(field.onDelete()));
        classBuilder.addMethod(createOnUpdateMethod(field.onUpdate()));
        classBuilder.addMethod(createNullableStringMethod("resolveWithClass", field.resolveWithClass()));

        classBuilder.addMethod(createTypeClassMethod("elementType", field.elementType()));
        classBuilder.addMethod(createTypeClassMethod("mapKeyType", field.mapKeyType()));
        classBuilder.addMethod(createTypeClassMethod("mapValueType", field.mapValueType()));
        classBuilder.addMethod(createBooleanMethod("indexed", field.indexed()));

        classBuilder.addMethod(createBooleanMethod("isJson", field.isJson()));
        classBuilder.addMethod(createJsonStorageKindMethod(field.jsonStorageKind()));
        classBuilder.addMethod(createNullableStringMethod("jsonColumnDefinition", field.jsonColumnDefinition()));
        classBuilder.addMethod(createJsonCodecMethod(field.jsonCodecClass()));
        classBuilder.addMethod(createBooleanMethod("jsonQueryable", field.jsonQueryable()));
        classBuilder.addMethod(createBooleanMethod("jsonPartialUpdate", field.jsonPartialUpdate()));
        classBuilder.addMethod(createJsonIndexesMethod(field.jsonIndexes()));


        return classBuilder.build();
    }

    public static MethodSpec createConsistencyMethod(Consistency consistency) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("consistency")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "Consistency"));

        if (consistency == null) {
            method.addStatement("return $T.NONE", ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "Consistency"));
        } else {
            method.addStatement(
                "return $T.$L",
                ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "Consistency"),
                consistency.name()
            );
        }

        return method.build();
    }

    public static MethodSpec createJsonStorageKindMethod(JsonStorageKind kind) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("jsonStorageKind")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("io.github.flameyossnowy.universal.api.meta", "JsonStorageKind"));

        if (kind == null) {
            method.addStatement("return $T.COLUMN", ClassName.get("io.github.flameyossnowy.universal.api.meta", "JsonStorageKind"));
        } else {
            method.addStatement(
                "return $T.$L",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "JsonStorageKind"),
                kind.name()
            );
        }
        return method.build();
    }

    public static MethodSpec createJsonCodecMethod(String codecClassName) {
        ClassName className = ClassName.get(Class.class);
        ClassName jsonCodec = ClassName.get("io.github.flameyossnowy.universal.api.json", "JsonCodec");
        TypeName jsonCodecWildcard = ParameterizedTypeName.get(jsonCodec, WildcardTypeName.subtypeOf(Object.class));
        TypeName returnType = ParameterizedTypeName.get(className, WildcardTypeName.subtypeOf(jsonCodecWildcard));

        MethodSpec.Builder method = MethodSpec.methodBuilder("jsonCodec")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        if (codecClassName == null) {
            method.addStatement("return (Class) $T.class", ClassName.get("io.github.flameyossnowy.universal.api.json", "DefaultJsonCodec"));
            return method.build();
        }

        ClassName codecClass = parseClassName(codecClassName);
        method.addStatement("return (Class) $T.class", codecClass);
        return method.build();
    }

    public static MethodSpec createJsonIndexesMethod(List<JsonIndexModel> indexes) {
        TypeName returnType = ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get("io.github.flameyossnowy.universal.api.meta", "JsonIndexModel"));

        MethodSpec.Builder method = MethodSpec.methodBuilder("jsonIndexes")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        if (indexes == null || indexes.isEmpty()) {
            method.addStatement("return $T.of()", ClassName.get(List.class));
            return method.build();
        }

        CodeBlock.Builder args = CodeBlock.builder();
        for (int i = 0; i < indexes.size(); i++) {
            JsonIndexModel idx = indexes.get(i);
            if (i > 0) {
                args.add(", ");
            }
            args.add("new $T($S, $L)",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "JsonIndexModel"),
                idx.path(),
                idx.unique()
            );
        }

        method.addStatement("return $T.of($L)", ClassName.get(List.class), args.build());
        return method.build();
    }

    public static MethodSpec createConditionMethod(Condition condition) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("condition")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("io.github.flameyossnowy.universal.api.annotations", "Condition"));

        if (condition == null) {
            method.addStatement("return null");
        } else {
            method.addStatement("""
                    return new $T() {
                        public Class<? extends java.lang.annotation.Annotation> annotationType() {
                            return $T.class;
                        }
                        public String value() {
                            return $S;
                        }
                    }""",
                ClassName.get("io.github.flameyossnowy.universal.api.annotations", "Condition"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations", "Condition"),
                condition.value()
            );
        }

        return method.build();
    }

    public static MethodSpec createTypeClassMethod(String name, TypeMirror mirror) {
        MethodSpec.Builder m = MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(Class.class),
                WildcardTypeName.subtypeOf(Object.class)
            ));

        if (mirror == null) {
            m.addStatement("return $T.class", Object.class);
        } else {
            m.addStatement("return $L", typeLiteral(mirror));
        }

        return m.build();
    }

    public static MethodSpec createOnDeleteMethod(OnDelete onDelete) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("onDelete")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("io.github.flameyossnowy.universal.api.annotations", "OnDelete"));

        if (onDelete == null) {
            method.addStatement("return null");
        } else {
            method.addStatement("""
                    return new $T() {
                        public Class<? extends java.lang.annotation.Annotation> annotationType() {
                            return $T.class;
                        }
                        public $T value() {
                            return $T.$L;
                        }
                    }""",
                ClassName.get("io.github.flameyossnowy.universal.api.annotations", "OnDelete"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations", "OnDelete"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "OnModify"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "OnModify"),
                onDelete.value().name()
            );
        }

        return method.build();
    }

    public static MethodSpec createOnUpdateMethod(OnUpdate onUpdate) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("onUpdate")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("io.github.flameyossnowy.universal.api.annotations", "OnUpdate"));

        if (onUpdate == null) {
            method.addStatement("return null");
        } else {
            method.addStatement("""
                    return new $T() {
                        public Class<? extends java.lang.annotation.Annotation> annotationType() {
                            return $T.class;
                        }
                        public $T value() {
                            return $T.$L;
                        }
                    }""",
                ClassName.get("io.github.flameyossnowy.universal.api.annotations", "OnUpdate"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations", "OnUpdate"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "OnModify"),
                ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "OnModify"),
                onUpdate.value().name()
            );
        }

        return method.build();
    }

    public static MethodSpec createStringMethod(String methodName, String value) {
        return MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addStatement("return $S", value)
            .build();
    }

    public static MethodSpec createBooleanMethod(String methodName, boolean value) {
        return MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addStatement("return $L", value)
            .build();
    }

    public static MethodSpec createDefaultValueProviderMethod(String providerClassName) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("defaultValueProvider")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(Class.class),
                WildcardTypeName.subtypeOf(Object.class)));

        if (providerClassName == null) {
            method.addStatement("return null");
        } else {
            // Parse the qualified class name
            ClassName providerClass = parseClassName(providerClassName);
            method.addStatement("return $T.class", providerClass);
        }

        return method.build();
    }

    public static ClassName parseClassName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot == -1) {
            return ClassName.get("", qualifiedName);
        }
        String packageName = qualifiedName.substring(0, lastDot);
        String simpleName = qualifiedName.substring(lastDot + 1);
        return ClassName.get(packageName, simpleName);
    }

    public static MethodSpec generateGetMethod(FieldModel field, ClassName entityClass) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("getValue")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(entityClass, "entity");

        method.addStatement("return entity.$L()", field.getterName());

        return method.build();
    }

    public static MethodSpec generateSetMethod(FieldModel field, ClassName entityClass) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("setValue")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(entityClass, "entity")
            .addParameter(Object.class, "value");

        TypeName fieldType = getTypeName(field.type());
        method.addStatement("entity.$L(($T) value)", field.setterName(), fieldType);

        return method.build();
    }

    public static MethodSpec createConstantMethod(String methodName, Class<?> returnType, Object value) {
        return MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addStatement("return $L", literal(value))
            .build();
    }

    public static MethodSpec createTypeMethod(TypeMirror type) {
        return MethodSpec.methodBuilder("type")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
            .addStatement("return $L", typeLiteral(type))
            .build();
    }

    public static MethodSpec createRelationshipKindMethod(RelationshipKind kind) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("relationshipKind")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipKind"));

        if (kind == null) {
            method.addStatement("return null");
        } else {
            method.addStatement("return $T.$L",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipKind"),
                kind.name());
        }

        return method.build();
    }

    public static MethodSpec createNullableStringMethod(String methodName, String value) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class);

        if (value == null) {
            method.addStatement("return null");
        } else {
            method.addStatement("return $S", value);
        }

        return method.build();
    }

    public static TypeName getTypeName(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return switch (type.getKind()) {
                case INT -> TypeName.INT;
                case LONG -> TypeName.LONG;
                case BOOLEAN -> TypeName.BOOLEAN;
                case DOUBLE -> TypeName.DOUBLE;
                case FLOAT -> TypeName.FLOAT;
                case BYTE -> TypeName.BYTE;
                case SHORT -> TypeName.SHORT;
                case CHAR -> TypeName.CHAR;
                default -> throw new IllegalStateException("Unknown primitive: " + type);
            };
        }
        return TypeName.get(type);
    }

    private static String typeLiteral(TypeMirror t) {
        if (t.getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) t;
            return ArrayTypeName.get(arrayType) + ".class";
        }
        return switch (t.getKind()) {
            case BOOLEAN -> "boolean.class";
            case BYTE    -> "byte.class";
            case SHORT   -> "short.class";
            case INT     -> "int.class";
            case LONG    -> "long.class";
            case CHAR    -> "char.class";
            case FLOAT   -> "float.class";
            case DOUBLE  -> "double.class";
            default -> {
                var dt = (DeclaredType) t;
                yield ((TypeElement) dt.asElement())
                    .getQualifiedName() + ".class";
            }
        };
    }
}
