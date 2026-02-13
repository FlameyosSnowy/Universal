package io.github.flameyossnowy.universal.checker;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.cache.BatchLoaderRegistry;
import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.LazyBatchContext;
import io.github.flameyossnowy.universal.api.handler.LazyBatchKey;
import io.github.flameyossnowy.universal.api.handler.LazyEntityRegistry;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.flameyossnowy.universal.checker.generator.RepositoryFieldModelGenerator;
import io.github.flameyossnowy.universal.checker.generator.RepositoryServicesGenerator;
import io.github.flameyossnowy.universal.checker.generator.ValueReaderGenerator;
import io.github.flameyossnowy.universal.checker.processor.AnnotationUtils;
import io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils;

import static io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils.qualifiedName;

public final class UnifiedFactoryGenerator {
    private final ProcessingEnvironment env;
    private final Filer filer;
    private final Types types;
    private final Elements elements;
    private final ValueReaderGenerator valueReaderGenerator;

    private final List<String> qualifiedNames = new ArrayList<>(4);

    public UnifiedFactoryGenerator(ProcessingEnvironment env, Filer filer) {
        this.env = env;
        this.filer = filer;
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.valueReaderGenerator = new ValueReaderGenerator(types, elements, filer);
    }

    public List<String> getQualifiedNames() {
        return qualifiedNames;
    }

    public void generate(RepositoryModel repo) {
        generateRepositoryModel(repo);
        generateObjectModel(repo);
        generateRelationshipLoader(repo);
        this.valueReaderGenerator.generateValueReader(repo, qualifiedNames);
    }

    private void generateRepositoryModel(RepositoryModel repo) {
        String implName = repo.entitySimpleName() + "_RepositoryModel_Impl";

        ClassName entityClass = ClassName.bestGuess(repo.entityQualifiedName());
        TypeName idClass = repo.primaryKeys().isEmpty() ?
            TypeName.get(Object.class) :
            TypeName.get(repo.primaryKeys().getFirst().type());

        ClassName repoInterface = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RepositoryModel");
        TypeName repoType = ParameterizedTypeName.get(repoInterface, entityClass, idClass.box());

        TypeSpec.Builder type = TypeSpec.classBuilder(implName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(repoType)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.UnifiedFactoryGenerator")
                .build());

        // INSTANCE
        type.addField(FieldSpec.builder(
                ClassName.bestGuess(implName),
                "INSTANCE",
                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $L()", implName)
            .build()
        );

        // ---------- Fields ----------
        RepositoryFieldModelGenerator.generateFieldModels(type, repo, entityClass);
        generateIndexes(type, repo);
        generateConstraints(type, repo);
        generateRelationshipCaches(type, repo, entityClass);
        generateRelationshipModels(type, repo, entityClass, idClass.box());
        RepositoryServicesGenerator.generateAuditLogger(type, repo);
        RepositoryServicesGenerator.generateExceptionHandler(type, repo);
        RepositoryServicesGenerator.generateLifecycleListener(type, repo);

        // ---------- Constructor ----------
        type.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build());

        type.addStaticBlock(CodeBlock.builder()
            .addStatement(
                "$T.add($S, INSTANCE)",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "GeneratedMetadata"),
                repo.tableName()
            )
            .build());

        // ---------- Getters ----------
        addRepoGetters(type, repo, entityClass, idClass.box());
        qualifiedNames.add(repo.packageName() + (repo.packageName() != null && !repo.packageName().isEmpty() ? "." : "")  + implName);
        write(repo.packageName(), type.build(), filer);
    }

    public static void addFieldsAndView(TypeSpec.Builder type, FieldSpec.Builder... types) {
        for (var fieldSpec : types) {
            type.addField(fieldSpec.build());
        }
    }

    private static void generateRelationshipCaches(TypeSpec.Builder type, RepositoryModel repo, ClassName entityClass) {
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        ClassName map = ClassName.get("java.util", "Map");
        ClassName collections = ClassName.get("java.util", "Collections");
        ParameterizedTypeName typeName = ParameterizedTypeName.get(fieldModel, entityClass);

        // OneToOne cache
        CodeBlock.Builder oneToOneInit = CodeBlock.builder();
        oneToOneInit.add("$T.unmodifiableMap($T.ofEntries(\n", collections, map);
        boolean hasOneToOne = false;
        for (int i = 0; i < repo.fields().size(); i++) {
            FieldModel field = repo.fields().get(i);
            if (field.relationshipKind() == RelationshipKind.ONE_TO_ONE) {
                if (hasOneToOne) oneToOneInit.add(",\n");
                oneToOneInit.add("  $T.entry($S, ($T) FIELDS[$L])", map, field.name(), typeName, i);
                hasOneToOne = true;
            }
        }
        if (!hasOneToOne) {
            oneToOneInit = CodeBlock.builder().add("$T.emptyMap()", collections);
        } else {
            oneToOneInit.add("\n))");
        }

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(map, ClassName.get(String.class), typeName),
                "ONE_TO_ONE_CACHE",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(oneToOneInit.build())
            .build());

        // OneToMany cache
        CodeBlock.Builder oneToManyInit = CodeBlock.builder();
        oneToManyInit.add("$T.unmodifiableMap($T.ofEntries(\n", collections, map);
        boolean hasOneToMany = false;
        for (int i = 0; i < repo.fields().size(); i++) {
            FieldModel field = repo.fields().get(i);
            if (field.relationshipKind() == RelationshipKind.ONE_TO_MANY) {
                if (hasOneToMany) oneToManyInit.add(",\n");
                oneToManyInit.add("  $T.entry($S, ($T) FIELDS[$L])", map, field.name(), typeName, i);
                hasOneToMany = true;
            }
        }
        if (!hasOneToMany) {
            oneToManyInit = CodeBlock.builder().add("$T.emptyMap()", collections);
        } else {
            oneToManyInit.add("\n))");
        }

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(map, ClassName.get(String.class), typeName),
                "ONE_TO_MANY_CACHE",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(oneToManyInit.build())
            .build());

        // ManyToOne cache
        CodeBlock.Builder manyToOneInit = CodeBlock.builder();
        manyToOneInit.add("$T.unmodifiableMap($T.ofEntries(\n", collections, map);
        boolean hasManyToOne = false;
        for (int i = 0; i < repo.fields().size(); i++) {
            FieldModel field = repo.fields().get(i);
            if (field.relationshipKind() == RelationshipKind.MANY_TO_ONE) {
                if (hasManyToOne) manyToOneInit.add(",\n");
                manyToOneInit.add("  $T.entry($S, ($T) FIELDS[$L])", map, field.name(), typeName, i);
                hasManyToOne = true;
            }
        }
        if (!hasManyToOne) {
            manyToOneInit = CodeBlock.builder().add("$T.emptyMap()", collections);
        } else {
            manyToOneInit.add("\n))");
        }

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(map, ClassName.get(String.class), typeName),
                "MANY_TO_ONE_CACHE",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(manyToOneInit.build())
            .build());
    }

    private static void generateRelationshipModels(
        TypeSpec.Builder type,
        RepositoryModel repo,
        ClassName entityClass,
        TypeName idClass
    ) {
        ClassName relationshipModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipModel");
        ClassName relationshipKind = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipKind");
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        ClassName collectionKind = ClassName.get("io.github.flameyossnowy.universal.api.factory", "CollectionKind");

        TypeName fieldModelType = ParameterizedTypeName.get(fieldModel, entityClass);
        TypeName relationshipModelType = ParameterizedTypeName.get(relationshipModel, entityClass, idClass);

        TypeName classWildcard = ParameterizedTypeName.get(
            ClassName.get(Class.class),
            WildcardTypeName.subtypeOf(Object.class)
        );

        TypeName relationshipModelWildcard = ParameterizedTypeName.get(
            relationshipModel,
            WildcardTypeName.subtypeOf(Object.class),
            WildcardTypeName.subtypeOf(Object.class)
        );

        TypeName fieldModelWildcard = ParameterizedTypeName.get(
            fieldModel,
            WildcardTypeName.subtypeOf(Object.class)
        );

        if (repo.relationships().isEmpty()) {
            type.addField(
                FieldSpec.builder(
                        ParameterizedTypeName.get(ClassName.get(List.class), relationshipModelType),
                        "RELATIONSHIPS_VIEW",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL
                    )
                    .initializer("$T.emptyList()", ClassName.get(Collections.class))
                    .build()
            );
            return;
        }

        List<String> relationshipClassNames = new ArrayList<>(repo.relationships().size());
        for (RelationshipModel rel : repo.relationships()) {
            int fieldIndex = -1;
            for (int i = 0; i < repo.fields().size(); i++) {
                if (repo.fields().get(i).name().equals(rel.fieldName())) {
                    fieldIndex = i;
                    break;
                }
            }

            if (fieldIndex == -1) {
                continue;
            }

            String relClassName = entityClass.simpleName() + "_" + rel.fieldName() + "_RelationshipModel";
            relationshipClassNames.add(relClassName);

            boolean isCollection = rel.relationshipKind() == RelationshipKind.ONE_TO_MANY;

            String collectionKindName = switch (rel.collectionKind()) {
                case LIST -> "LIST";
                case SET -> "SET";
                case QUEUE -> "QUEUE";
                case DEQUE -> "DEQUE";
                default -> "OTHER";
            };

            boolean isOwning = rel.relationshipKind() == RelationshipKind.MANY_TO_ONE;

            ClassName targetClass = ClassName.bestGuess(rel.targetEntityQualifiedName());

            TypeSpec.Builder relType = TypeSpec.classBuilder(relClassName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(relationshipModelType);

            relType.addMethod(MethodSpec.methodBuilder("fieldName")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(String.class))
                .addStatement("return $S", rel.fieldName())
                .build());

            relType.addMethod(MethodSpec.methodBuilder("relationshipKind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(relationshipKind)
                .addStatement("return $T.$L", relationshipKind, rel.relationshipKind().name())
                .build());

            relType.addMethod(MethodSpec.methodBuilder("lazy")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement("return $L", rel.lazy())
                .build());

            relType.addMethod(MethodSpec.methodBuilder("isCollection")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement("return $L", isCollection)
                .build());

            relType.addMethod(MethodSpec.methodBuilder("collectionKind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(collectionKind)
                .addStatement("return $T.$L", collectionKind, collectionKindName)
                .build());

            relType.addMethod(MethodSpec.methodBuilder("targetEntityType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(classWildcard)
                .addStatement("return $T.class", targetClass)
                .build());

            relType.addMethod(MethodSpec.methodBuilder("getTargetRelationshipModel")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(relationshipModelWildcard)
                .addStatement("return null")
                .build());

            relType.addMethod(MethodSpec.methodBuilder("getFieldModel")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldModelType)
                .addStatement("return ($T) FIELDS[$L]", fieldModelType, fieldIndex)
                .build());

            relType.addMethod(MethodSpec.methodBuilder("getOwningFieldModel")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldModelWildcard)
                .addStatement("return null")
                .build());

            relType.addMethod(MethodSpec.methodBuilder("isOwning")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement("return $L", isOwning)
                .build());

            relType.addMethod(MethodSpec.methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(Object.class))
                .addParameter(entityClass, "entity")
                .addStatement("return getFieldModel().getValue(entity)")
                .build());

            relType.addMethod(MethodSpec.methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(entityClass, "entity")
                .addParameter(ClassName.get(Object.class), "value")
                .addStatement("getFieldModel().setValue(entity, value)")
                .build());

            relType.addMethod(MethodSpec.methodBuilder("cascadesInsert")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement("return false")
                .build());

            relType.addMethod(MethodSpec.methodBuilder("cascadesUpdate")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement("return false")
                .build());

            relType.addMethod(MethodSpec.methodBuilder("cascadesDelete")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement("return false")
                .build());

            type.addType(relType.build());
        }

        CodeBlock.Builder listInit = CodeBlock.builder();
        listInit.add("$T.of(\n", ClassName.get(List.class));
        for (int i = 0; i < relationshipClassNames.size(); i++) {
            if (i != 0) {
                listInit.add(",\n");
            }
            listInit.add("  new $L()", relationshipClassNames.get(i));
        }
        listInit.add("\n)");

        type.addField(
            FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), relationshipModelType),
                    "RELATIONSHIPS_VIEW",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL
                )
                .initializer(listInit.build())
                .build()
        );
    }

    private static void addRepoGetters(TypeSpec.Builder type, RepositoryModel repo,
                                       ClassName entityClass, TypeName idClass) {
        type.addMethod(constant("packageName", String.class, repo.packageName()));
        type.addMethod(constant("entitySimpleName", String.class, repo.entitySimpleName()));
        type.addMethod(constant("entityQualifiedName", String.class, repo.entityQualifiedName()));
        type.addMethod(constant("tableName", String.class, repo.tableName()));
        type.addMethod(constant("isRecord", boolean.class, repo.isRecord()));

        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        TypeName fieldModelType = ParameterizedTypeName.get(fieldModel, entityClass);

        type.addMethod(MethodSpec.methodBuilder("fields")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), fieldModelType))
            .addStatement("return FIELDS_VIEW")
            .build());

        type.addMethod(MethodSpec.methodBuilder("indexes")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(List.class),
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "IndexModel")))
            .addStatement("return INDEXES_VIEW")
            .build());

        type.addMethod(MethodSpec.methodBuilder("isCacheable")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addStatement("return $L", repo.cacheable())
            .build());

        type.addMethod(MethodSpec.methodBuilder("isGlobalCacheable")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addStatement("return $L", repo.globalCacheable())
            .build());

        Set<String> resolverClasses = new HashSet<>();

        for (FieldModel field : repo.fields()) {
            if (field.resolveWithClass() != null) {
                resolverClasses.add(field.resolveWithClass());
            }
        }

        ClassName typeResolverClass = ClassName.get(
            "io.github.flameyossnowy.universal.api.resolver",
            "TypeResolver"
        );

        TypeName typeResolverWildcard =
            ParameterizedTypeName.get(
                typeResolverClass,
                WildcardTypeName.subtypeOf(Object.class)
            );

        TypeName resolverExtends =
            WildcardTypeName.subtypeOf(typeResolverWildcard);

        TypeName classOfResolver =
            ParameterizedTypeName.get(
                ClassName.get(Class.class),
                resolverExtends
            );

        TypeName returnType =
            ParameterizedTypeName.get(
                ClassName.get(List.class),
                classOfResolver
            );


        MethodSpec.Builder method = MethodSpec.methodBuilder("getRequiredResolvers")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        if (resolverClasses.isEmpty()) {
            method.addStatement("return $T.emptyList()", ClassName.get(Collections.class));
        } else {
            CodeBlock.Builder listInit = CodeBlock.builder();
            listInit.add("return $T.of(\n", ClassName.get(List.class));

            boolean first = true;
            for (String resolverQualifiedName : resolverClasses) {
                if (!first) {
                    listInit.add(",\n");
                }

                ClassName resolverClass = RepositoryFieldModelGenerator.parseClassName(resolverQualifiedName);
                listInit.add("    $T.class", resolverClass);
                first = false;
            }

            listInit.add("\n)");
            method.addStatement(listInit.build());
        }

        type.addMethod(method.build());

        if (repo.cacheable()) {
            CacheConfig config = repo.cacheConfig();

            type.addField(
                FieldSpec.builder(
                        ClassName.get(
                            "io.github.flameyossnowy.universal.api.cache",
                            "CacheConfig"
                        ),
                        "CACHE_CONFIG",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL
                    )
                    .initializer(
                        "new $T($L, $T.$L)",
                        ClassName.get(
                            "io.github.flameyossnowy.universal.api.cache",
                            "CacheConfig"
                        ),
                        config.maxSize(),
                        ClassName.get(
                            "io.github.flameyossnowy.universal.api.annotations.enums",
                            "CacheAlgorithmType"
                        ),
                        config.cacheAlgorithmType().name()
                    )
                    .build()
            );

            type.addMethod(MethodSpec.methodBuilder("getCacheConfig")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("io.github.flameyossnowy.universal.api.cache", "CacheConfig"))
                .addStatement("return CACHE_CONFIG")
                .build());
        } else {
            type.addMethod(MethodSpec.methodBuilder("getCacheConfig")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("io.github.flameyossnowy.universal.api.cache", "CacheConfig"))
                .addStatement("return $T.none()", ClassName.get("io.github.flameyossnowy.universal.api.cache", "CacheConfig"))
                .build());
        }

        type.addMethod(MethodSpec.methodBuilder("constraints")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(List.class),
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "ConstraintModel")))
            .addStatement("return CONSTRAINTS_VIEW")
            .build());

        type.addMethod(MethodSpec.methodBuilder("getPrimaryKey")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(fieldModelType)
            .addStatement("return fieldByName($S)",
                repo.primaryKeys().isEmpty() ? "" : repo.primaryKeys().getFirst().name())
            .build());

        type.addMethod(MethodSpec.methodBuilder("primaryKeys")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(String.class)))
            .addStatement("return PRIMARY_KEYS")
            .build());

        MethodSpec.Builder getPkValue = MethodSpec.methodBuilder("getPrimaryKeyValue")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(idClass)
            .addParameter(entityClass, "entity");

        if (repo.primaryKeys().isEmpty()) {
            getPkValue.addStatement("throw new $T($S)",
                IllegalStateException.class,
                "No primary key defined for " + repo.entitySimpleName());
        } else {
            getPkValue.addStatement("$T pk = getPrimaryKey()", fieldModelType);
            getPkValue.addStatement("return ($T) pk.getValue(entity)", idClass);
        }
        type.addMethod(getPkValue.build());

        MethodSpec.Builder setPkValue = MethodSpec.methodBuilder("setPrimaryKeyValue")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(entityClass, "entity")
            .addParameter(idClass, "id");

        if (repo.primaryKeys().isEmpty()) {
            setPkValue.addStatement("throw new $T($S)",
                IllegalStateException.class,
                "No primary key defined for " + repo.entitySimpleName());
        } else {
            setPkValue.addStatement("$T pk = getPrimaryKey()", fieldModelType);
            setPkValue.addStatement("pk.setValue(entity, id)");
        }
        type.addMethod(setPkValue.build());

        type.addMethod(MethodSpec.methodBuilder("getEntityClass")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), entityClass))
            .addStatement("return $T.class", entityClass)
            .build());

        type.addMethod(MethodSpec.methodBuilder("getIdClass")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), idClass.box()))
            .addStatement("return $T.class", idClass)
            .build());

        type.addMethod(MethodSpec.methodBuilder("getFetchPageSize")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("return " + repo.fetchPageSize())
            .build());

        type.addMethod(MethodSpec.methodBuilder("getAuditLogger")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.listener", "AuditLogger"),
                ClassName.bestGuess(repo.entityQualifiedName())
            ))
            .addStatement("return AUDIT_LOGGER")
            .build());

        type.addMethod(MethodSpec.methodBuilder("getExceptionHandler")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.exceptions.handler", "ExceptionHandler"),
                entityClass,
                idClass.box(),
                WildcardTypeName.subtypeOf(Object.class)
            ))
            .addStatement("return EXCEPTION_HANDLER")
            .build());

        type.addMethod(MethodSpec.methodBuilder("getEntityLifecycleListener")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(
                    "io.github.flameyossnowy.universal.api.listener",
                    "EntityLifecycleListener"
                ),
                ClassName.bestGuess(repo.entityQualifiedName())
            ))
            .addStatement("return LIFECYCLE")
            .build());

        ClassName relationshipModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipModel");
        TypeName relationshipModelType = ParameterizedTypeName.get(relationshipModel, entityClass, idClass);

        type.addMethod(MethodSpec.methodBuilder("getRelationships")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), relationshipModelType))
            .addStatement("return RELATIONSHIPS_VIEW")
            .build());

        type.addMethod(MethodSpec.methodBuilder("getOneToOneCache")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                fieldModelType))
            .addStatement("return ONE_TO_ONE_CACHE")
            .build());

        type.addMethod(MethodSpec.methodBuilder("getOneToManyCache")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                fieldModelType))
            .addStatement("return ONE_TO_MANY_CACHE")
            .build());

        type.addMethod(MethodSpec.methodBuilder("getManyToOneCache")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                fieldModelType))
            .addStatement("return MANY_TO_ONE_CACHE")
            .build());

        MethodSpec.Builder lookup = MethodSpec.methodBuilder("fieldByName")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(fieldModelType)
            .addParameter(String.class, "n");

        lookup.beginControlFlow("switch (n)");
        int i = 0;
        for (FieldModel f : repo.fields()) {
            lookup.addStatement("case $S: return FIELDS[$L]", f.name(), i++);
        }
        lookup.addStatement("default: return null");
        lookup.endControlFlow();

        type.addMethod(lookup.build());

        if (repo.globalCacheable()) {
            ParameterizedTypeName typeName = ParameterizedTypeName.get(ClassName.get(SessionCache.class), entityClass, idClass.box());
            type.addMethod(MethodSpec.methodBuilder("createGlobalSessionCache")
                .returns(typeName)
                .addStatement("return new $T();", repo.sessionCache())
                .build());
        }
    }

    private static MethodSpec constant(String name, Class<?> type, Object value) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(name)
            .addModifiers(Modifier.PUBLIC)
            .returns(type)
            .addStatement("return $L", literal(value));

        return builder.build();
    }

    public static String literal(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\""; // quote strings
        } else if (value instanceof Character) {
            return "'" + value + "'";
        } else if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        } else {
            throw new IllegalArgumentException("Unsupported constant type: " + value.getClass());
        }
    }

    private static void generateIndexes(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName indexModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "IndexModel");

        if (repo.indexes().isEmpty()) {
            addFieldsAndView(type, FieldSpec.builder(
                    ArrayTypeName.of(indexModel),
                    "INDEXES",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T[0]", indexModel), FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), indexModel),
                    "INDEXES_VIEW",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.of()", List.class));
        } else {
            CodeBlock.Builder arrInit = CodeBlock.builder().add("{\n");
            for (IndexModel idx : repo.indexes()) {
                arrInit.add("  new IndexModelImpl($S, new String[]{",
                    idx.name());

                for (int i = 0; i < idx.fields().size(); i++) {
                    arrInit.add("$S", idx.fields().get(i));
                    if (i < idx.fields().size() - 1) {
                        arrInit.add(", ");
                    }
                }

                arrInit.add("}, IndexType.$L),\n", idx.type().name());
            }
            arrInit.add("}");

            addFieldsAndView(type, FieldSpec.builder(
                    ArrayTypeName.of(indexModel),
                    "INDEXES",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(arrInit.build()), FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), indexModel),
                    "INDEXES_VIEW",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(
                    "$T.unmodifiableList($T.asList(INDEXES))",
                    ClassName.get("java.util", "Collections"),
                    ClassName.get("java.util", "Arrays")
                ));
        }
    }

    private static void generateConstraints(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName constraintModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "ConstraintModel");

        //ClassName constraintModelImpl = ClassName.get("io.github.flameyossnowy.universal.api.meta", "ConstraintModelImpl");

        if (repo.constraints().isEmpty()) {
            addFieldsAndView(type, FieldSpec.builder(
                    ArrayTypeName.of(constraintModel),
                    "CONSTRAINTS",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T[0]", constraintModel), FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), constraintModel),
                    "CONSTRAINTS_VIEW",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.of()", List.class));
        } else {
            CodeBlock.Builder arrInit = CodeBlock.builder().add("{\n");
            for (ConstraintModel c : repo.constraints()) {
                arrInit.add("  new ConstraintModelImpl($S, new String[]{",
                    c.name());

                for (int i = 0; i < c.fields().size(); i++) {
                    arrInit.add("$S", c.fields().get(i));
                    if (i < c.fields().size() - 1) {
                        arrInit.add(", ");
                    }
                }

                arrInit.add("}),\n");
            }
            arrInit.add("}");

            addFieldsAndView(type, FieldSpec.builder(
                    ArrayTypeName.of(constraintModel),
                    "CONSTRAINTS",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(arrInit.build()), FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), constraintModel),
                    "CONSTRAINTS_VIEW",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(
                    "$T.unmodifiableList($T.asList(CONSTRAINTS))",
                    ClassName.get("java.util", "Collections"),
                    ClassName.get("java.util", "Arrays")
                ));
        }
    }

    private void generateObjectModel(RepositoryModel repo) {
        ClassName entityType = ClassName.bestGuess(repo.entityQualifiedName());

        List<FieldModel> fieldModels = repo.primaryKeys();
        TypeName idType = fieldModels.isEmpty()
            ? TypeName.get(Object.class)
            : TypeName.get(fieldModels.getFirst().type()).box();

        for (RelationshipModel rel : repo.relationships()) {
            if (!rel.lazy()) continue;

            switch (rel.relationshipKind()) {
                case ONE_TO_ONE, MANY_TO_ONE -> generateLazyEntityProxy(repo, rel);
                case ONE_TO_MANY -> generateLazyCollectionProxy(repo, rel);
            }
        }

        String className = repo.entitySimpleName() + "_ObjectModel";

        ParameterizedTypeName repoModelTypeName = ParameterizedTypeName.get(
            ClassName.get(io.github.flameyossnowy.universal.api.meta.RepositoryModel.class),
            entityType,
            idType.box()
        );
        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.UnifiedFactoryGenerator")
                .build())
            .addField(FieldSpec.builder(repoModelTypeName, "repositoryModel", Modifier.PRIVATE, Modifier.FINAL)
                .build())
            .addSuperinterface(ParameterizedTypeName.get(
                ClassName.get(ObjectModel.class),
                entityType,
                idType.box()
            ))
            .addMethod(MethodSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder(repoModelTypeName, "repositoryModel").build())
                .addStatement("this.repositoryModel = repositoryModel")
                .build())
            .addMethod(generateConstruct(repo, entityType))
            .addMethod(generatePopulateRelationships(repo, entityType, idType))
            .addMethod(generateInsertEntity(repo, entityType))
            .addMethod(generateInsertCollectionEntities(repo, entityType, idType))
            .addMethod(generateGetId(repo, entityType, idType))
            .addMethod(generateGetIdType(idType))
            .addMethod(generateGetEntityType(entityType))
            .addStaticBlock(CodeBlock.builder()
                .addStatement("io.github.flameyossnowy.universal.api.meta.GeneratedObjectFactories.add($S, $T.class, $T.class, (model) -> new $L(model))",
                    repo.tableName(),
                    entityType,
                    idType.box(),
                    repo.entitySimpleName() + "_ObjectModel")
                .build());

        qualifiedNames.add(repo.packageName() + (repo.packageName() != null && !repo.packageName().isEmpty() ? "." : "")  + className);
        write(repo.packageName(), builder.build(), filer);
    }

    private static MethodSpec generateGetId(RepositoryModel repo, ClassName entityType, TypeName idType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("getId")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(idType)
            .addParameter(entityType, "entity");

        List<FieldModel> fieldModels = repo.primaryKeys();
        if (fieldModels.isEmpty()) {
            m.addStatement("return null");
            return m.build();
        }
        FieldModel pkField = fieldModels.getFirst();
        m.addStatement("return entity.$L()", pkField.getterName());

        return m.build();
    }

    private record IdFieldInfo(String name, TypeMirror type) {
    }

    private IdFieldInfo resolveIdFieldInfo(String entityQualifiedName) {
        TypeElement entity = elements.getTypeElement(entityQualifiedName);
        if (entity == null) {
            return null;
        }

        for (Element e : entity.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!AnnotationUtils.hasAnnotation(e, io.github.flameyossnowy.universal.api.annotations.Id.class.getCanonicalName())) {
                continue;
            }
            VariableElement ve = (VariableElement) e;
            return new IdFieldInfo(ve.getSimpleName().toString(), ve.asType());
        }

        return null;
    }

    private TypeMirror normalizeIdTypeMirror(TypeMirror type) {
        if (type == null) {
            return null;
        }

        if (type.getKind().isPrimitive()) {
            return types.boxedClass((PrimitiveType) type).asType();
        }

        return type;
    }

    private void generateRelationshipLoader(RepositoryModel repo) {
        TypeName entityType = ClassName.get(repo.entityType());

        List<FieldModel> fieldModels = repo.primaryKeys();
        if (fieldModels.isEmpty()) {
            return;
        }
        TypeName idType = TypeName.get(fieldModels.getFirst().type());

        String className = repo.entitySimpleName() + "_RelationshipLoader";
        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.UnifiedFactoryGenerator")
                .build())
            .addSuperinterface(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.factory", "RelationshipLoader"),
                entityType,
                idType.box()
            ));

        builder.addStaticBlock(CodeBlock.builder()
            .addStatement(
                "io.github.flameyossnowy.universal.api.meta.GeneratedRelationshipLoaders" +
                    ".<$T, $T>add($S, (handler, collectionHandler, model) -> new $L(handler, collectionHandler, model))",
                entityType,
                idType.box(),
                repo.tableName(),
                repo.entitySimpleName() + "_RelationshipLoader"
            )
            .build());

        // Fields
        builder.addField(FieldSpec.builder(
            ClassName.get("io.github.flameyossnowy.universal.api.handler", "RelationshipHandler"),
            "handler",
            Modifier.PRIVATE, Modifier.FINAL
        ).build());

        builder.addField(FieldSpec.builder(
            ClassName.get("io.github.flameyossnowy.universal.api.handler", "CollectionHandler"),
            "collectionHandler",
            Modifier.PRIVATE, Modifier.FINAL
        ).build());

        ParameterizedTypeName typeName = ParameterizedTypeName.get(
            ClassName.get("io.github.flameyossnowy.universal.api.meta", "RepositoryModel"),
            entityType,
            idType.box()
        );

        builder.addField(FieldSpec.builder(
            typeName,
            "repositoryModel",
            Modifier.PRIVATE, Modifier.FINAL
        ).build());

        // Caches
        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ClassName.get(Object.class))),
                "oneToOneCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ParameterizedTypeName.get(ClassName.get(Collection.class), ClassName.get(Object.class)))),
                "oneToManyCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ClassName.get(Object.class))),
                "manyToOneCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        // Collection caches
        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ParameterizedTypeName.get(ClassName.get(Collection.class), ClassName.get(Object.class)))),
                "listCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ParameterizedTypeName.get(ClassName.get(Collection.class), ClassName.get(Object.class)))),
                "setCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(Object.class), ClassName.get(Object.class)))),
                "mapCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        builder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    idType.box(),
                    ParameterizedTypeName.get(ClassName.get(Map.class),
                        ClassName.get(String.class),
                        ArrayTypeName.of(ClassName.get(Object.class)))),
                "arrayCache",
                Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());

        // Constructor
        builder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.handler", "RelationshipHandler"), "handler")
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.handler", "CollectionHandler"), "collectionHandler")
            .addParameter(typeName, "repositoryModel")
            .addStatement("this.handler = handler")
            .addStatement("this.collectionHandler = collectionHandler")
            .addStatement("this.repositoryModel = repositoryModel")
            .build());

        // Generate basic relationship methods
        builder.addMethod(generateOneToOneWithCache(repo, idType));
        builder.addMethod(generateOneToManyWithCache(repo, idType));
        builder.addMethod(generateManyToOneWithCache(repo, idType));

        // Generate collection loaders
        builder.addMethod(generateLoadListWithCache(repo, idType));
        builder.addMethod(generateLoadSetWithCache(repo, idType));
        builder.addMethod(generateLoadMapWithCache(repo, idType));
        builder.addMethod(generateLoadArrayWithCache(repo, idType));

        // Generate invalidateRelationshipsForId
        builder.addMethod(MethodSpec.methodBuilder("invalidateRelationshipsForId")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(idType.box(), "id")
            .addStatement("oneToOneCache.remove(id)")
            .addStatement("oneToManyCache.remove(id)")
            .addStatement("manyToOneCache.remove(id)")
            .addStatement("listCache.remove(id)")
            .addStatement("setCache.remove(id)")
            .addStatement("mapCache.remove(id)")
            .addStatement("arrayCache.remove(id)")
            .build());

        // Generate clear method
        builder.addMethod(MethodSpec.methodBuilder("clear")
            .addModifiers(Modifier.PUBLIC)
            .addStatement("oneToOneCache.clear()")
            .addStatement("oneToManyCache.clear()")
            .addStatement("manyToOneCache.clear()")
            .addStatement("listCache.clear()")
            .addStatement("setCache.clear()")
            .addStatement("mapCache.clear()")
            .addStatement("arrayCache.clear()")
            .build());

        qualifiedNames.add(repo.packageName() + (repo.packageName() != null && !repo.packageName().isEmpty() ? "." : "")  + className);
        write(repo.packageName(), builder.build(), filer);
    }

    private static MethodSpec generateOneToOneWithCache(RepositoryModel repo, TypeName idType) {
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
            if (rel.relationshipKind() == RelationshipKind.ONE_TO_ONE) {
                m.addStatement(
                    "case $S: return ($T) oneToOneCache"
                        + ".computeIfAbsent(id, f -> new $T<>())"
                        + ".computeIfAbsent(fieldName, k -> handler.handleOneToOneRelationship(id, repositoryModel.fieldByName(fieldName)))",
                    rel.fieldName(),
                    e,
                    ConcurrentHashMap.class
                );
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown OneToOne field: ");
        m.endControlFlow();

        return m.build();
    }

    private static MethodSpec generateManyToOneWithCache(RepositoryModel repo, TypeName idType) {
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
            if (rel.relationshipKind() == RelationshipKind.MANY_TO_ONE) {
                m.addStatement(
                    "case $S: return ($T) manyToOneCache"
                        + ".computeIfAbsent(id, f -> new $T<>())"
                        + ".computeIfAbsent(fieldName, k -> handler.handleManyToOneRelationship(id, repositoryModel.fieldByName(fieldName)))",
                    rel.fieldName(),
                    e,
                    ConcurrentHashMap.class
                );
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown ManyToOne field: ");
        m.endControlFlow();

        return m.build();
    }

    private static MethodSpec generateOneToManyWithCache(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
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
            if (rel.relationshipKind() == RelationshipKind.ONE_TO_MANY) {
                m.addStatement(
                    "case $S: return ($T) oneToManyCache"
                        + ".computeIfAbsent(id, f -> new $T<>())"
                        + ".computeIfAbsent(fieldName, k -> handler.handleOneToManyRelationship(id, repositoryModel.fieldByName(fieldName)))",
                    rel.fieldName(),
                    ParameterizedTypeName.get(collection, e),
                    ConcurrentHashMap.class
                );
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown OneToMany field: ");
        m.endControlFlow();

        return m.build();
    }

    private static MethodSpec generateLoadListWithCache(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        ClassName collection = ClassName.get(Collection.class);

        MethodSpec.Builder m = MethodSpec.methodBuilder("loadList")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(ParameterizedTypeName.get(collection, e))
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (FieldModel field : repo.fields()) {
            if (!field.relationship() && isListType(field.type())) {
                m.addStatement(
                    "case $S: return ($T) listCache"
                        + ".computeIfAbsent(id, k -> new $T<>())"
                        + ".computeIfAbsent(fieldName, fn -> ($T) collectionHandler.fetchCollection(id, fn, elementType, $T.$L, repositoryModel))",
                    field.name(),
                    ParameterizedTypeName.get(collection, e),
                    ClassName.get(ConcurrentHashMap.class),
                    ParameterizedTypeName.get(Collection.class, Object.class),
                    ClassName.get(io.github.flameyossnowy.universal.api.factory.CollectionKind.class),
                    field.collectionKind().name()
                );
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown List field: ");
        m.endControlFlow();

        return m.build();
    }

    private static MethodSpec generateLoadSetWithCache(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        ClassName collection = ClassName.get(Collection.class);

        MethodSpec.Builder m = MethodSpec.methodBuilder("loadSet")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(e)
            .returns(ParameterizedTypeName.get(collection, e))
            .addParameter(String.class, "fieldName")
            .addParameter(idType.box(), "id")
            .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), e), "elementType");

        m.beginControlFlow("switch (fieldName)");
        for (FieldModel field : repo.fields()) {
            if (!field.relationship() && isSetType(field.type())) {
                m.addStatement(
                    "case $S: return ($T) setCache"
                        + ".computeIfAbsent(id, k -> new $T<>())"
                        + ".computeIfAbsent(fieldName, fn -> ($T) collectionHandler.fetchCollection(id, fn, elementType, $T.$L, repositoryModel))",
                    field.name(),
                    ParameterizedTypeName.get(collection, e),
                    ClassName.get(ConcurrentHashMap.class),
                    ParameterizedTypeName.get(Collection.class, Object.class),
                    ClassName.get(CollectionKind.class),
                    field.collectionKind().name()
                );
            }
        }
        m.addStatement("default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown Set field: ");
        m.endControlFlow();

        return m.build();
    }

    private static MethodSpec generateLoadMapWithCache(RepositoryModel repo, TypeName idType) {
        TypeVariableName e = TypeVariableName.get("E");
        TypeVariableName k = TypeVariableName.get("K");

        ClassName map = ClassName.get(Map.class);

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
            if (!field.relationship() && isMapType(field.type())) {
                m.addStatement(
                    "case $S: return ($T) mapCache"
                        + ".computeIfAbsent(id, k2 -> new $T<>())"
                        + ".computeIfAbsent(fieldName, fn -> "
                        + "($T) collectionHandler.fetchMap(id, fn, keyType, valueType, repositoryModel))",
                    field.name(),
                    ParameterizedTypeName.get(map, e, k),
                    ConcurrentHashMap.class,
                    ParameterizedTypeName.get(Map.class, Object.class, Object.class)
                );
            }
        }

        m.addStatement(
            "default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown Map field: "
        );
        m.endControlFlow();

        return m.build();
    }

    private static MethodSpec generateLoadArrayWithCache(RepositoryModel repo, TypeName idType) {
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
                        + ".computeIfAbsent(fieldName, fn -> "
                        + "collectionHandler.fetchArray(id, fn, componentType, repositoryModel))",
                    field.name(),
                    ArrayTypeName.of(e),
                    ConcurrentHashMap.class
                );
            }
        }

        m.addStatement(
            "default: throw new $T($S + fieldName)",
            IllegalArgumentException.class,
            "Unknown Array field: "
        );
        m.endControlFlow();

        return m.build();
    }

    private static boolean isListType(TypeMirror type) {
        String typeName = type.toString();
        return typeName.startsWith("java.util.List") || typeName.startsWith("java.util.ArrayList");
    }

    private static boolean isSetType(TypeMirror type) {
        String typeName = type.toString();
        return typeName.startsWith("java.util.Set") || typeName.startsWith("java.util.HashSet");
    }

    private static boolean isMapType(TypeMirror type) {
        String typeName = type.toString();
        return typeName.startsWith("java.util.Map") || typeName.startsWith("java.util.HashMap");
    }

    private MethodSpec generateConstruct(RepositoryModel repo, ClassName entityType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("construct")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(entityType)
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.factory", "ValueReader"), "r");

        if (repo.isRecord()) {
            m.addStatement("throw new $T($S)", UnsupportedOperationException.class, "Records are constructed via canonical constructor");
            return m.build();
        }

        m.addStatement("$T entity = new $T()", entityType, entityType);
        int index = 0;

        for (FieldModel field : repo.fields()) {
            if (field.relationship()) continue;
            if (field.autoIncrement()) {
                m.addStatement("entity.$L(($T) r.getId())", field.setterName(), ClassName.get(field.type()));
                continue;
            }
            if (field.isNotCollection(types, elements)) {
                m.addStatement("entity.$L(($T) r.read($L))", field.setterName(), ClassName.get(field.type()), index++);
            } else {
                m.beginControlFlow("if (r.getDatabaseResult().supportsArraysNatively())")
                    .addStatement("entity.$L(($T) r.read($L))", field.setterName(), ClassName.get(field.type()), index++)
                    .endControlFlow();
            }
        }

        m.addStatement("return entity");

        return m.build();
    }

    private MethodSpec generateInsertEntity(RepositoryModel repo, ClassName entityType) {
        ClassName dbParams = ClassName.get("io.github.flameyossnowy.universal.api.params", "DatabaseParameters");
        ClassName repoMetadata = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RepositoryModel");
        ClassName generatedMetadata = ClassName.get("io.github.flameyossnowy.universal.api.meta", "GeneratedMetadata");
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");

        TypeMirror thisIdTypeMirror = repo.primaryKeys().isEmpty() ? null : repo.primaryKeys().getFirst().type();
        TypeName thisIdTypeName = thisIdTypeMirror == null ? TypeName.get(Object.class) : TypeName.get(thisIdTypeMirror);
        TypeName thisIdTypeNameBoxed = thisIdTypeName.isPrimitive() ? thisIdTypeName.box() : thisIdTypeName;

        MethodSpec.Builder m = MethodSpec.methodBuilder("insertEntity")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addException(Exception.class)
            .addParameter(dbParams, "stmt")
            .addParameter(entityType, "entity");

        for (FieldModel field : repo.fields()) {
            if (field.autoIncrement()) {
                continue;
            }

            if (!field.insertable()) {
                continue;
            }

            TypeMirror fieldTypeMirror = field.type();
            String fieldName = field.name();
            if (isCollectionOrMapOrArray(fieldTypeMirror)) {
                continue;
            }

            String valueVar = "value_" + fieldName;
            TypeName fieldType = RepositoryFieldModelGenerator.getTypeName(field.type());

            m.addStatement("$T $L = entity.$L()", fieldType, valueVar, field.getterName());

            if (field.relationship() && field.relationshipKind() != null) {
                RelationshipKind kind = field.relationshipKind();

                if (kind == RelationshipKind.ONE_TO_ONE || kind == RelationshipKind.MANY_TO_ONE) {
                    m.beginControlFlow("if ($L != null)", valueVar);

                    ClassName relatedEntityClass = ClassName.bestGuess(field.typeQualifiedName());
                    IdFieldInfo relatedId = resolveIdFieldInfo(field.typeQualifiedName());

                    if (relatedId == null) {
                        env.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Related entity '" + field.typeQualifiedName() + "' has no @Id field (required for relationship FK binding)"
                        );
                        m.addStatement("throw new $T($S)",
                            IllegalArgumentException.class,
                            "Related entity has no @Id field for relationship field: " + fieldName);
                    } else {
                        if (thisIdTypeMirror != null) {
                            TypeMirror relatedIdType = normalizeIdTypeMirror(relatedId.type());
                            TypeMirror thisIdType = normalizeIdTypeMirror(thisIdTypeMirror);
                            if (!types.isSameType(thisIdType, relatedIdType)) {
                                env.getMessager().printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Relationship '" + repo.entityQualifiedName() + "." + fieldName + "' points to entity '" +
                                        field.typeQualifiedName() + "' with a different @Id type. Expected: " + thisIdType + ", got: " + relatedIdType
                                );
                            }
                        }

                        TypeName relatedRepoType = ParameterizedTypeName.get(
                            repoMetadata,
                            relatedEntityClass,
                            thisIdTypeNameBoxed
                        );

                        m.addStatement("$T relatedRepo = $T.getByEntityClass($T.class)",
                            relatedRepoType,
                            generatedMetadata,
                            relatedEntityClass);

                        m.beginControlFlow("if (relatedRepo != null)");

                        TypeName relatedFieldModelType = ParameterizedTypeName.get(fieldModel, relatedEntityClass);
                        m.addStatement("$T pkField = relatedRepo.fieldByName($S)", relatedFieldModelType, relatedId.name());
                        m.beginControlFlow("if (pkField != null)");
                        m.addStatement("Object pkValue = pkField.getValue($L)", valueVar);
                        m.addStatement("stmt.set($S, pkValue, pkField.type())", fieldName);
                        m.nextControlFlow("else");
                        m.addStatement("throw new $T($S + $S)",
                            IllegalArgumentException.class,
                            "Primary key not found for relationship field: ",
                            fieldName);
                        m.endControlFlow();

                        m.nextControlFlow("else");
                        m.addStatement("throw new $T($S + $S)",
                            IllegalArgumentException.class,
                            "Repository metadata not found for: ",
                            field.typeQualifiedName());
                        m.endControlFlow();
                    }

                    m.nextControlFlow("else");
                    m.addStatement("stmt.set($S, null, $T.class)", fieldName, thisIdTypeNameBoxed);
                    m.endControlFlow();

                    continue;
                }
                // Note: ONE_TO_MANY is already filtered out by the collection check at the top
            }

            // Handle scalar fields with type information
            m.addStatement("stmt.set($S, $L, $T.class)", fieldName, valueVar, fieldType);
        }

        return m.build();
    }

    // Helper method to check if a type is a collection, map, or array
    private boolean isCollectionOrMapOrArray(TypeMirror type) {
        // Check for arrays first
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }

        // Use the processing environment's Types and Elements
        return TypeMirrorUtils.isCollection(env.getTypeUtils(), env.getElementUtils(), type) ||
            TypeMirrorUtils.isMap(env.getTypeUtils(), env.getElementUtils(), type);
    }

    private static MethodSpec generateInsertCollectionEntities(
        RepositoryModel repo,
        ClassName entityType,
        TypeName idType
    ) {
        ClassName dbParams = ClassName.get(
            "io.github.flameyossnowy.universal.api.params",
            "DatabaseParameters"
        );
        ClassName collectionHandler = ClassName.get(
            "io.github.flameyossnowy.universal.api.handler",
            "CollectionHandler"
        );

        MethodSpec.Builder m = MethodSpec.methodBuilder("insertCollectionEntities")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addException(Exception.class)
            .addParameter(entityType, "entity")
            .addParameter(idType.box(), "id")
            .addParameter(dbParams, "params");

        boolean hasCollections = false;

        // Get the collection handler once at the start
        m.addStatement("$T handler = params.getCollectionHandler()", collectionHandler);

        for (FieldModel field : repo.fields()) {
            if (field.relationship()) {
                continue; // Skip relationships
            }

            TypeMirror fieldType = field.type();
            String typeName = fieldType.toString();
            String fieldName = field.name();
            String getterCall = "entity." + field.getterName() + "()";

            // Handle List/Set/Collection - INLINE with exact type
            if (typeName.startsWith("java.util.List") ||
                typeName.startsWith("java.util.Set") ||
                typeName.startsWith("java.util.Collection")) {

                if (!hasCollections) {
                    hasCollections = true;
                }

                // Extract generic type
                TypeMirror elementType = extractGenericType(fieldType, 0);
                String elementTypeName = elementType != null ?
                    elementType.toString() : "Object";

                ClassName elementClass = ClassName.bestGuess(elementTypeName);

                // Direct call - no instanceof check needed since we know the type
                m.addStatement(
                    "handler.insertCollection(id, $S, $L, $T.class, this.repositoryModel)",
                    fieldName,
                    getterCall,
                    elementClass
                );

                continue;
            }

            // Handle Map - INLINE with exact type
            if (typeName.startsWith("java.util.Map")) {
                if (!hasCollections) {
                    hasCollections = true;
                }

                // Extract key and value types
                TypeMirror keyType = extractGenericType(fieldType, 0);
                TypeMirror valueType = extractGenericType(fieldType, 1);
                String keyTypeName = keyType != null ? keyType.toString() : "Object";
                String valueTypeName = valueType != null ? valueType.toString() : "Object";

                ClassName keyClass = ClassName.bestGuess(keyTypeName);
                ClassName valueClass = ClassName.bestGuess(valueTypeName);

                // Check if it's a multimap (Map<K, Collection<V>>)
                boolean isMultiMap = isMultiMap(valueType);

                if (isMultiMap) {
                    TypeMirror collectionValueType = extractGenericType(valueType, 0);
                    String collectionValueTypeName = collectionValueType != null ?
                        collectionValueType.toString() : "Object";
                    ClassName collectionValueClass = ClassName.bestGuess(collectionValueTypeName);

                    // Direct call for multimap
                    m.addStatement(
                        "handler.insertMultiMap(id, $S, $L, $T.class, $T.class, this.repositoryModel)",
                        fieldName,
                        getterCall,
                        keyClass,
                        collectionValueClass
                    );
                } else {
                    // Direct call for regular map
                    m.addStatement(
                        "handler.insertMap(id, $S, $L, $T.class, $T.class, this.repositoryModel)",
                        fieldName,
                        getterCall,
                        keyClass,
                        valueClass
                    );
                }

                continue;
            }

            // Handle arrays - INLINE with exact type
            if (fieldType.getKind() == TypeKind.ARRAY) {
                if (!hasCollections) {
                    hasCollections = true;
                }

                ArrayType arrayType = (ArrayType) fieldType;
                TypeMirror componentType = arrayType.getComponentType();
                String componentTypeName = componentType.toString();
                ClassName componentClass = ClassName.bestGuess(componentTypeName);

                // Direct call for array
                m.addStatement(
                    "handler.insertArray(id, $S, $L, $T.class, this.repositoryModel)",
                    fieldName,
                    getterCall,
                    componentClass
                );
            }
        }

        if (!hasCollections) {
            m.addComment("No collection fields to insert");
        }

        return m.build();
    }

    // Helper method to extract generic type arguments
    private static TypeMirror extractGenericType(TypeMirror type, int index) {
        if (type instanceof DeclaredType declaredType) {
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() > index) {
                return typeArguments.get(index);
            }
        }
        return null;
    }

    // Helper to check if a type is a Collection type (for multimap detection)
    private static boolean isMultiMap(TypeMirror valueType) {
        if (valueType == null) return false;
        String typeName = valueType.toString();
        return typeName.startsWith("java.util.List") ||
            typeName.startsWith("java.util.Set") ||
            typeName.startsWith("java.util.Collection");
    }

    private static MethodSpec generateGetIdType(TypeName idType) {
        return MethodSpec.methodBuilder("getIdType")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), idType.box()))
            .addStatement("return $T.class", idType)
            .build();
    }

    private static MethodSpec generateGetEntityType(ClassName entityType) {
        return MethodSpec.methodBuilder("getEntityType")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), entityType))
            .addStatement("return $T.class", entityType)
            .build();
    }

    // ---------------- Populate Relationships ----------------

    private static MethodSpec generatePopulateRelationships(RepositoryModel repo, ClassName entityType, TypeName idClass) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("populateRelationships")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(entityType, "entity")
            .addParameter(idClass, "id")
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.factory", "RelationshipLoader"), "loader");

        for (RelationshipModel rel : repo.relationships()) {
            String call = generateCallSyntax(rel, repo);

            Object[] args = rel.relationshipKind() == RelationshipKind.ONE_TO_MANY
                ? (rel.lazy() ? new Object[] { rel.setterName(), rel.fieldType() } : new Object[] { rel.setterName(), rel.fieldType(), rel.targetTypeName() })
                : new Object[] {
                    rel.setterName(),
                    rel.targetTypeName(),
                    rel.targetTypeName()
                };

            m.addStatement(
                "entity.$L(($T) " + call + ")",
                args
            );
        }

        return m.build();
    }

    private static MethodSpec generatePopulateMethod(RepositoryModel repo, ClassName entityType) {
        TypeName typeName = ClassName.get(repo.primaryKeys().getFirst().type());
        MethodSpec.Builder m = MethodSpec.methodBuilder("populate")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(entityType, "entity")
            .addParameter(typeName, "id");

        for (RelationshipModel rel : repo.relationships()) {
            String call = generateCallSyntax(rel, repo);
            m.addStatement("entity.$L(" + call + ")", rel.setterName(), typeName);
        }

        return m.build();
    }

    private static String generateCallSyntax(RelationshipModel rel, RepositoryModel repo) {
        String loaderMethod = switch (rel.relationshipKind()) {
            case ONE_TO_ONE -> "oneToOne";
            case ONE_TO_MANY -> "oneToMany";
            case MANY_TO_ONE -> "manyToOne";
        };

        String call = "loader." + loaderMethod + "(\"" + rel.fieldName() + "\", id, $T.class)";

        if (rel.lazy()) {
            call = rel.relationshipKind() == RelationshipKind.ONE_TO_MANY
                ? "new " + lazyCollectionProxyName(repo, rel) + "(id, loader)"
                : "new " + lazyEntityProxyName(repo, rel) + "(id, loader, $T.class)";
        }

        return call;
    }

    private void generateLazyEntityProxy(RepositoryModel repo, RelationshipModel rel) {
        String proxyName = lazyEntityProxyName(repo, rel);

        //ClassName ownerType = ClassName.bestGuess(repo.entityQualifiedName());

        TypeName idType = ClassName.get(repo.primaryKeys().getFirst().type()).box();

        ClassName targetType = ClassName.bestGuess(rel.targetEntityQualifiedName());

        ParameterizedTypeName loaderType = ParameterizedTypeName.get(ClassName.get(RelationshipLoader.class), ClassName.get(repo.entityType()), idType);

        TypeSpec.Builder type =
            TypeSpec.classBuilder(proxyName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(targetType)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.UnifiedFactoryGenerator")
                    .addMember("comments", "$S", "Lazy loading proxy for " + rel.fieldName())
                    .build());

        type.addField(idType.box(), "ownerId", Modifier.PRIVATE, Modifier.FINAL);
        type.addField(loaderType, "loader", Modifier.PRIVATE, Modifier.FINAL);
        type.addField(targetType, "value", Modifier.PRIVATE);
        type.addField(boolean.class, "loaded", Modifier.PRIVATE);

        type.addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(idType.box(), "ownerId")
                .addParameter(loaderType, "loader")
                .addStatement("this.ownerId = ownerId")
                .addStatement("this.loader = loader")
                .build()
        );

        type.addMethod(
            MethodSpec.methodBuilder("load")
                .addModifiers(Modifier.PRIVATE)
                .returns(targetType)
                .beginControlFlow("if (!loaded)")
                .addStatement(
                    "$T resolved = ($T) $T.resolve($L, ownerId)",
                    targetType,
                    targetType,
                    LazyEntityRegistry.class,
                    batchKeyLiteral(repo, rel)
                )
                .beginControlFlow("if (resolved != null)")
                .addStatement("value = resolved")
                .addStatement("loaded = true")
                .addStatement("return value")
                .endControlFlow()
                .addStatement(
                    "$T.<$T>current().register($L, ownerId)",
                    LazyBatchContext.class,
                    idType.box(),
                    batchKeyLiteral(repo, rel)
                )
                .addStatement("$T.markPending()", BatchLoaderRegistry.class)
                .endControlFlow()
                .addStatement("return value")
                .build()
        );

        overrideEntityMethods(type, targetType);
        write(repo.packageName(), type.build(), filer);
    }

    private void generateLazyCollectionProxy(RepositoryModel repo, RelationshipModel rel) {
        String proxyName = lazyCollectionProxyName(repo, rel);

        TypeName idType = ClassName.get(repo.primaryKeys().getFirst().type()).box();
        ClassName elementType = ClassName.bestGuess(rel.targetEntityQualifiedName());

        CollectionKind kind = rel.collectionKind();

        // Determine the correct collection interface and abstract base class
        ClassName collectionRaw;
        ClassName abstractBase;
        TypeName collectionType;
        TypeName abstractType;
        ClassName emptyFactory;

        switch (kind) {
            case LIST -> {
                System.out.println("list");
                collectionRaw = ClassName.get(List.class);
                abstractBase = ClassName.get("java.util", "AbstractList");
                emptyFactory = ClassName.get(List.class);
                collectionType = ParameterizedTypeName.get(collectionRaw, elementType);
                abstractType = ParameterizedTypeName.get(abstractBase, elementType);
            }
            case SET -> {
                System.out.println("set");
                collectionRaw = ClassName.get(Set.class);
                abstractBase = ClassName.get("java.util", "AbstractSet");
                emptyFactory = ClassName.get(Set.class);
                collectionType = ParameterizedTypeName.get(collectionRaw, elementType);
                abstractType = ParameterizedTypeName.get(abstractBase, elementType);
            }
            case MAP -> {
                // MAP needs special handling with key/value types
                // Throw for now or implement separately
                throw new UnsupportedOperationException(
                    "MAP relationships require separate proxy implementation"
                );
            }
            default -> { // COLLECTION or null
                System.out.println("coll or null: " + kind);
                collectionRaw = ClassName.get(Collection.class);
                abstractBase = ClassName.get("java.util", "AbstractCollection");
                emptyFactory = ClassName.get(List.class);
                collectionType = ParameterizedTypeName.get(collectionRaw, elementType);
                abstractType = ParameterizedTypeName.get(abstractBase, elementType);
            }
        }

        ParameterizedTypeName loaderType = ParameterizedTypeName.get(
            ClassName.get(RelationshipLoader.class),
            ClassName.get(repo.entityType()),
            idType
        );

        TypeSpec.Builder type =
            TypeSpec.classBuilder(proxyName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(abstractType)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.UnifiedFactoryGenerator")
                    .addMember("comments", "$S", "Lazy collection loading proxy for " + rel.fieldName())
                    .build());

        // Fields - use specific collection type, not Collection
        type.addField(idType.box(), "ownerId", Modifier.PRIVATE, Modifier.FINAL);
        type.addField(loaderType, "loader", Modifier.PRIVATE, Modifier.FINAL);
        type.addField(collectionType, "value", Modifier.PRIVATE);
        type.addField(boolean.class, "loaded", Modifier.PRIVATE);

        type.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(idType.box(), "ownerId")
            .addParameter(loaderType, "loader")
            .addStatement("this.ownerId = ownerId")
            .addStatement("this.loader = loader")
            .build());

        // Load method - returns specific collection type
        type.addMethod(MethodSpec.methodBuilder("load")
            .addModifiers(Modifier.PRIVATE)
            .returns(collectionType)
            .beginControlFlow("if (!loaded)")
            .addStatement(
                "$T resolved = ($T) $T.resolve($L, ownerId)",
                collectionType,
                collectionType,
                LazyEntityRegistry.class,
                batchKeyLiteral(repo, rel)
            )
            .beginControlFlow("if (resolved != null)")
            .addStatement("value = resolved")
            .addStatement("loaded = true")
            .addStatement("return value")
            .endControlFlow()
            .addStatement(
                "$T.<$T>current().register($L, ownerId)",
                LazyBatchContext.class,
                idType.box(),
                batchKeyLiteral(repo, rel)
            )
            .addStatement("$T.markPending()", BatchLoaderRegistry.class)
            .endControlFlow()
            .beginControlFlow("if (value == null)")
            .addStatement("value = $T.of()", emptyFactory)
            .endControlFlow()
            .addStatement("return value")
            .build());

        // Add required abstract methods based on collection type
        switch (kind) {
            case LIST -> {
                // AbstractList requires: get(int) and size()
                type.addMethod(MethodSpec.methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(elementType)
                    .addParameter(TypeName.INT, "index")
                    .addStatement("return load().get(index)")
                    .build());

                type.addMethod(MethodSpec.methodBuilder("size")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.INT)
                    .addStatement("return load().size()")
                    .build());
            }
            case SET -> {
                // AbstractSet requires: iterator() and size()
                type.addMethod(MethodSpec.methodBuilder("iterator")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(
                        ClassName.get("java.util", "Iterator"),
                        elementType
                    ))
                    .addStatement("return load().iterator()")
                    .build());

                type.addMethod(MethodSpec.methodBuilder("size")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.INT)
                    .addStatement("return load().size()")
                    .build());
            }
            default -> {
                // AbstractCollection requires: iterator() and size()
                type.addMethod(MethodSpec.methodBuilder("size")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.INT)
                    .addStatement("return load().size()")
                    .build());

                type.addMethod(MethodSpec.methodBuilder("iterator")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(
                        ClassName.get("java.util", "Iterator"),
                        elementType
                    ))
                    .addStatement("return load().iterator()")
                    .build());
            }
        }

        write(repo.packageName(), type.build(), filer);
    }

    private static CodeBlock batchKeyLiteral(RepositoryModel repo, RelationshipModel rel) {
        return CodeBlock.of("new $T($S, $S)",
            LazyBatchKey.class,
            repo.tableName(),
            rel.fieldName()
        );
    }

    private void overrideEntityMethods(TypeSpec.Builder type, ClassName targetType) {
        TypeElement element =
            env.getElementUtils().getTypeElement(targetType.canonicalName());

        for (Element e : element.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) continue;

            ExecutableElement m = (ExecutableElement) e;

            if (m.getModifiers().contains(Modifier.STATIC)) continue;
            if (m.getModifiers().contains(Modifier.FINAL)) continue;
            if (m.getModifiers().contains(Modifier.PRIVATE)) continue;
            if (m.getSimpleName().contentEquals("<init>")) continue;

            MethodSpec.Builder override = MethodSpec.overriding(m);

            boolean seen = false;
            StringBuilder acc = null;
            for (VariableElement p : m.getParameters()) {
                String string = p.getSimpleName().toString();
                if (!seen) {
                    seen = true;
                    acc = new StringBuilder(string);
                } else {
                    acc.append(", ").append(string);
                }
            }
            String args = seen ? acc.toString() : "";

            if (m.getReturnType().getKind() == TypeKind.VOID) {
                override.addStatement("load().$L($L)", m.getSimpleName(), args);
            } else {
                override.addStatement("return load().$L($L)", m.getSimpleName(), args);
            }

            type.addMethod(override.build());
        }
    }

    private static String lazyEntityProxyName(RepositoryModel repo, RelationshipModel rel) {
        return repo.entitySimpleName() + "_" + rel.fieldName() + "_LazyProxy";
    }

    private static String lazyCollectionProxyName(RepositoryModel repo, RelationshipModel rel) {
        return repo.entitySimpleName() + "_" + rel.fieldName() + "_LazyCollectionProxy";
    }

    // --------------------------------------------------

    public static void write(String pkg, TypeSpec spec, Filer filer) {
        try {
            JavaFile.builder(pkg, spec).build().writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}