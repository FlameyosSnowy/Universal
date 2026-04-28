package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.GeneratedRepositoryFactory;
import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.*;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Single Responsibility: generate the {@code *_RepositoryModel_Impl} class.
 *
 * <p>Delegates field-model and service generation to existing helpers
 * ({@link RepositoryFieldModelGenerator}, {@link RepositoryServicesGenerator}).
 */
public final class RepositoryModelGenerator {

    private final Filer filer;
    private final Elements elements;
    private final Types types;
    private final Messager messager;

    public RepositoryModelGenerator(Filer filer, Elements elements, Types types, Messager messager) {
        this.filer = filer;
        this.elements = elements;
        this.types = types;
        this.messager = messager;
    }

    public String generate(RepositoryModel repo, List<String> qualifiedNames) {
        String    implName   = repo.entitySimpleName() + "_RepositoryModel_Impl";
        ClassName entityClass = ClassName.bestGuess(repo.entityQualifiedName());
        TypeName  idClass    = repo.primaryKeys().isEmpty()
            ? TypeName.get(Object.class)
            : TypeName.get(repo.primaryKeys().getFirst().type());

        ClassName repoInterface = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RepositoryModel");
        TypeName  repoType      = ParameterizedTypeName.get(repoInterface, entityClass, idClass.box());

        TypeSpec.Builder type = TypeSpec.classBuilder(implName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(repoType)
            .addSuperinterface(TypeName.get(GeneratedRepositoryFactory.class))
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.generator.UnifiedFactoryGenerator")
                .build());

        // Singleton INSTANCE
        type.addField(FieldSpec.builder(ClassName.bestGuess(implName), "INSTANCE",
                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $L()", implName)
            .build());

        // Fields
        RepositoryFieldModelGenerator.generateFieldModels(type, repo, entityClass, elements, types, messager);
        generateIndexes(type, repo);
        generateConstraints(type, repo);
        generateRelationshipCaches(type, repo, entityClass);
        generateRelationshipModels(type, repo, entityClass, idClass.box());
        RepositoryServicesGenerator.generateAuditLogger(type, repo);
        RepositoryServicesGenerator.generateExceptionHandler(type, repo);
        RepositoryServicesGenerator.generateLifecycleListener(type, repo);

        // Private constructor
        type.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());

        // Static initializer – register in GeneratedMetadata
        type.addStaticBlock(CodeBlock.builder()
            .addStatement("$T.add($S, INSTANCE)",
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "GeneratedMetadata"),
                repo.tableName())
            .build());

        addGetters(type, repo, entityClass, idClass.box());
        addParameterNameMap(type, repo);
        addFieldIndexes(type, repo, entityClass);

        String qualifiedName = GeneratorUtils.qualifiedName(repo.packageName(), implName);
        qualifiedNames.add(qualifiedName);
        GeneratorUtils.write(repo.packageName(), type.build(), filer);
        return qualifiedName;
    }


    private static void addGetters(TypeSpec.Builder type, RepositoryModel repo,
                                    ClassName entityClass, TypeName idClass) {
        type.addMethod(GeneratorUtils.constantMethod("packageName",           String.class,  repo.packageName()));
        type.addMethod(GeneratorUtils.constantMethod("entitySimpleName",      String.class,  repo.entitySimpleName()));
        type.addMethod(GeneratorUtils.constantMethod("entityQualifiedName",   String.class,  repo.entityQualifiedName()));
        type.addMethod(GeneratorUtils.constantMethod("tableName",             String.class,  repo.tableName()));
        type.addMethod(GeneratorUtils.constantMethod("isRecord",              boolean.class, repo.isRecord()));

        ClassName fieldModel     = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        TypeName  fieldModelType = ParameterizedTypeName.get(fieldModel, entityClass);

        type.addMethod(MethodSpec.methodBuilder("fields")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), fieldModelType))
            .addStatement("return FIELDS_VIEW").build());

        type.addMethod(MethodSpec.methodBuilder("indexes")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class),
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "IndexModel")))
            .addStatement("return INDEXES_VIEW").build());

        type.addMethod(MethodSpec.methodBuilder("isCacheable")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN).addStatement("return $L", repo.cacheable()).build());

        type.addMethod(MethodSpec.methodBuilder("isGlobalCacheable")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN).addStatement("return $L", repo.globalCacheable()).build());

        addRequiredResolvers(type, repo);
        addCredentialsProviderMethod(type, repo);
        addCacheConfigMethod(type, repo);

        type.addMethod(MethodSpec.methodBuilder("constraints")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class),
                ClassName.get("io.github.flameyossnowy.universal.api.meta", "ConstraintModel")))
            .addStatement("return CONSTRAINTS_VIEW").build());

        type.addMethod(MethodSpec.methodBuilder("getPrimaryKey")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(fieldModelType)
            .addStatement("return fieldByName($S)",
                repo.primaryKeys().isEmpty() ? "" : repo.primaryKeys().getFirst().name())
            .build());

        type.addMethod(MethodSpec.methodBuilder("primaryKeys")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(String.class)))
            .addStatement("return PRIMARY_KEYS").build());

        addPkValueMethods(type, repo, entityClass, idClass, fieldModelType);
        addEntityClassMethods(type, repo, entityClass, idClass);
        addRelationshipGetters(type, repo, entityClass, idClass, fieldModelType);
        addFieldByName(type, repo, fieldModelType);
        addColumnFieldByName(type, repo, fieldModelType);
        addGlobalCacheMethod(type, repo, entityClass, idClass);
    }

    private static void addRequiredResolvers(TypeSpec.Builder type, RepositoryModel repo) {
        Set<String> resolverClasses = new HashSet<>();
        for (FieldModel field : repo.fields()) {
            if (field.resolveWithClass() != null) resolverClasses.add(field.resolveWithClass());
        }

        ClassName typeResolverClass  = ClassName.get("io.github.flameyossnowy.universal.api.resolver", "TypeResolver");
        ClassName supplierClass      = ClassName.get("java.util.function", "Supplier");
        TypeName  typeResolverWild   = ParameterizedTypeName.get(typeResolverClass, WildcardTypeName.subtypeOf(Object.class));
        TypeName  supplierOfResolver = ParameterizedTypeName.get(supplierClass, typeResolverWild);
        TypeName  returnType         = ParameterizedTypeName.get(ClassName.get(List.class), supplierOfResolver);

        MethodSpec.Builder m = MethodSpec.methodBuilder("getRequiredResolvers")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        if (resolverClasses.isEmpty()) {
            m.addStatement("return $T.emptyList()", ClassName.get(Collections.class));
        } else {
            CodeBlock.Builder init = CodeBlock.builder().add("return $T.of(\n", ClassName.get(List.class));
            boolean first = true;
            for (String qname : resolverClasses) {
                if (!first) init.add(",\n");
                // Generate lambda: () -> new ResolverClass()
                init.add("    () -> new $T()", RepositoryFieldModelGenerator.parseClassName(qname));
                first = false;
            }
            m.addStatement(init.add("\n)").build());
        }
        type.addMethod(m.build());
    }

    private static void addCredentialsProviderMethod(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName supplierClass = ClassName.get("java.util.function", "Supplier");
        TypeName returnType = ParameterizedTypeName.get(supplierClass, ClassName.get(String.class));

        MethodSpec.Builder m = MethodSpec.methodBuilder("credentialsProvider")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        if (!repo.hasCredentialsProvider()) {
            m.addStatement("return null");
        } else {
            // Generate lambda: () -> new CredentialsProviderClass().get()
            m.addStatement("return () -> new $T().get()",
                RepositoryFieldModelGenerator.parseClassName(repo.credentialsProviderQualifiedName()));
        }
        type.addMethod(m.build());
    }

    private static void addCacheConfigMethod(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName cacheConfigClass = ClassName.get("io.github.flameyossnowy.universal.api.cache", "CacheConfig");

        if (repo.cacheable()) {
            CacheConfig config = repo.cacheConfig();
            type.addField(FieldSpec.builder(cacheConfigClass, "CACHE_CONFIG",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T($L, $T.$L)", cacheConfigClass, config.maxSize(),
                    ClassName.get("io.github.flameyossnowy.universal.api.annotations.enums", "CacheAlgorithmType"),
                    config.cacheAlgorithmType().name())
                .build());
            type.addMethod(MethodSpec.methodBuilder("getCacheConfig")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(cacheConfigClass).addStatement("return CACHE_CONFIG").build());
        } else {
            type.addMethod(MethodSpec.methodBuilder("getCacheConfig")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(cacheConfigClass)
                .addStatement("return $T.none()", cacheConfigClass).build());
        }
    }

    private static void addPkValueMethods(TypeSpec.Builder type, RepositoryModel repo,
                                           ClassName entityClass, TypeName idClass,
                                           TypeName fieldModelType) {
        MethodSpec.Builder get = MethodSpec.methodBuilder("getPrimaryKeyValue")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(idClass).addParameter(entityClass, "entity");
        if (repo.primaryKeys().isEmpty()) {
            get.addStatement("throw new $T($S)", IllegalStateException.class,
                "No primary key defined for " + repo.entitySimpleName());
        } else {
            get.addStatement("$T pk = getPrimaryKey()", fieldModelType);
            get.addStatement("return ($T) pk.getValue(entity)", idClass);
        }
        type.addMethod(get.build());

        MethodSpec.Builder set = MethodSpec.methodBuilder("setPrimaryKeyValue")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID).addParameter(entityClass, "entity").addParameter(idClass, "id");
        if (repo.primaryKeys().isEmpty()) {
            set.addStatement("throw new $T($S)", IllegalStateException.class,
                "No primary key defined for " + repo.entitySimpleName());
        } else {
            set.addStatement("$T pk = getPrimaryKey()", fieldModelType);
            set.addStatement("pk.setValue(entity, id)");
        }
        type.addMethod(set.build());
    }

    private static void addEntityClassMethods(TypeSpec.Builder type, RepositoryModel repo,
                                               ClassName entityClass, TypeName idClass) {
        type.addMethod(MethodSpec.methodBuilder("getEntityClass")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), entityClass))
            .addStatement("return $T.class", entityClass).build());

        type.addMethod(MethodSpec.methodBuilder("getIdClass")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), idClass.box()))
            .addStatement("return $T.class", idClass).build());

        type.addMethod(MethodSpec.methodBuilder("getFetchPageSize")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT).addStatement("return " + repo.fetchPageSize()).build());

        type.addMethod(MethodSpec.methodBuilder("getAuditLogger")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.listener", "AuditLogger"),
                ClassName.bestGuess(repo.entityQualifiedName())))
            .addStatement("return AUDIT_LOGGER").build());

        type.addMethod(MethodSpec.methodBuilder("getExceptionHandler")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.exceptions.handler", "ExceptionHandler"),
                entityClass, idClass.box(), WildcardTypeName.subtypeOf(Object.class)))
            .addStatement("return EXCEPTION_HANDLER").build());

        type.addMethod(MethodSpec.methodBuilder("getEntityLifecycleListener")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.listener", "EntityLifecycleListener"),
                ClassName.bestGuess(repo.entityQualifiedName())))
            .addStatement("return LIFECYCLE").build());
    }

    private static void addRelationshipGetters(TypeSpec.Builder type, RepositoryModel repo,
                                                ClassName entityClass, TypeName idClass,
                                                TypeName fieldModelType) {
        ClassName relationshipModel     = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipModel");
        TypeName  relationshipModelType = ParameterizedTypeName.get(relationshipModel, entityClass, idClass);

        type.addMethod(MethodSpec.methodBuilder("getRelationships")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List.class), relationshipModelType))
            .addStatement("return RELATIONSHIPS_VIEW").build());

        type.addMethod(MethodSpec.methodBuilder("getOneToOneCache")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), fieldModelType))
            .addStatement("return ONE_TO_ONE_CACHE").build());

        type.addMethod(MethodSpec.methodBuilder("getOneToManyCache")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), fieldModelType))
            .addStatement("return ONE_TO_MANY_CACHE").build());

        type.addMethod(MethodSpec.methodBuilder("getManyToOneCache")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class), fieldModelType))
            .addStatement("return MANY_TO_ONE_CACHE").build());
    }

    private static void addFieldByName(TypeSpec.Builder type, RepositoryModel repo,
                                        TypeName fieldModelType) {
        MethodSpec.Builder lookup = MethodSpec.methodBuilder("fieldByName")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(fieldModelType).addParameter(String.class, "n");

        lookup.beginControlFlow("switch (n)");
        int i = 0;
        for (FieldModel f : repo.fields()) {
            lookup.addStatement("case $S: return FIELDS[$L]", f.name(), i++);
        }
        lookup.addStatement("default: return null");
        lookup.endControlFlow();
        type.addMethod(lookup.build());
    }

    private static void addColumnFieldByName(TypeSpec.Builder type, RepositoryModel repo,
                                             TypeName fieldModelType) {
        MethodSpec.Builder lookup = MethodSpec.methodBuilder("columnFieldByName")
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(fieldModelType)
            .addParameter(String.class, "c");

        lookup.beginControlFlow("if (c == null)");
        lookup.addStatement("return null");
        lookup.endControlFlow();

        lookup.addStatement("String n = c.toLowerCase($T.ROOT)", ClassName.get("java.util", "Locale"));
        lookup.beginControlFlow("switch (n)");

        int i = 0;
        for (FieldModel f : repo.fields()) {
            String col = f.columnName();
            if (col == null || col.isBlank()) {
                i++;
                continue;
            }
            lookup.addStatement("case $S: return FIELDS[$L]", col.toLowerCase(java.util.Locale.ROOT), i);
            i++;
        }

        lookup.addStatement("default: return null");
        lookup.endControlFlow();
        type.addMethod(lookup.build());
    }

    private static void addGlobalCacheMethod(TypeSpec.Builder type, RepositoryModel repo,
                                              ClassName entityClass, TypeName idClass) {
        if (!repo.globalCacheable()) return;
        ParameterizedTypeName typeName = ParameterizedTypeName.get(
            ClassName.get(SessionCache.class), entityClass, idClass.box());
        type.addMethod(MethodSpec.methodBuilder("createGlobalSessionCache")
            .returns(typeName)
            .addStatement("return new $T();", repo.sessionCache())
            .build());
    }

    private static void addParameterNameMap(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName map = ClassName.get("java.util", "Map");
        ClassName collections = ClassName.get("java.util", "Collections");
        TypeName mapType = ParameterizedTypeName.get(map, ClassName.get(String.class), ClassName.get(String.class));

        CodeBlock.Builder init = CodeBlock.builder();
        init.add("$T.unmodifiableMap($T.ofEntries(\n", collections, map);

        boolean first = true;
        // Add field names and column names mapping to themselves
        for (FieldModel f : repo.fields()) {
            String fieldName = f.name();
            String columnName = f.columnName();

            // Map field name to column name
            if (!first) init.add(",\n");
            init.add("  $T.entry($S, $S)", map, fieldName, columnName != null ? columnName : fieldName);
            first = false;

            // Map column name to itself (if different from field name)
            if (columnName != null && !columnName.equals(fieldName)) {
                init.add(",\n");
                init.add("  $T.entry($S, $S)", map, columnName, columnName);
            }
        }

        if (first) {
            // No fields - use empty map
            type.addField(FieldSpec.builder(mapType, "PARAMETER_NAME_MAP",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.emptyMap()", map)
                .build());
        } else {
            init.add("\n))");
            type.addField(FieldSpec.builder(mapType, "PARAMETER_NAME_MAP",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(init.build())
                .build());
        }

        type.addMethod(MethodSpec.methodBuilder("getParameterNameMappings")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(mapType)
            .addStatement("return PARAMETER_NAME_MAP")
            .build());
    }

    private static void addFieldIndexes(TypeSpec.Builder type, RepositoryModel repo, ClassName entityClass) {
        ClassName map = ClassName.get("java.util", "Map");
        ClassName list = ClassName.get("java.util", "List");
        ClassName collections = ClassName.get("java.util", "Collections");
        ClassName relationshipKind = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipKind");
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");

        TypeName fieldModelType = ParameterizedTypeName.get(fieldModel, entityClass);
        TypeName listType = ParameterizedTypeName.get(list, fieldModelType);
        TypeName mapType = ParameterizedTypeName.get(map, relationshipKind, listType);

        // Group fields by relationship kind
        Map<RelationshipKind, List<String>> fieldsByKind = new EnumMap<>(RelationshipKind.class);
        int fieldIndex = 0;
        for (FieldModel f : repo.fields()) {
            if (f.relationship()) {
                fieldsByKind.computeIfAbsent(f.relationshipKind(), k -> new ArrayList<>()).add("FIELDS[" + fieldIndex + "]");
            }
            fieldIndex++;
        }

        if (fieldsByKind.isEmpty()) {
            type.addField(FieldSpec.builder(mapType, "FIELD_INDEXES",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.emptyMap()", map)
                .build());
        } else {
            CodeBlock.Builder init = CodeBlock.builder();
            init.add("$T.unmodifiableMap($T.ofEntries(\n", collections, map);

            boolean first = true;
            for (Map.Entry<RelationshipKind, List<String>> entry : fieldsByKind.entrySet()) {
                if (!first) init.add(",\n");
                first = false;

                RelationshipKind kind = entry.getKey();
                List<String> fieldRefs = entry.getValue();

                // Build list initializer
                init.add("  $T.entry($T.$L, $T.of(", map, relationshipKind, kind.name(), list);
                for (int i = 0; i < fieldRefs.size(); i++) {
                    if (i > 0) init.add(", ");
                    init.add("($T) $L", fieldModelType, fieldRefs.get(i));
                }
                init.add("))");
            }
            init.add("\n))");

            type.addField(FieldSpec.builder(mapType, "FIELD_INDEXES",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(init.build())
                .build());
        }

        type.addMethod(MethodSpec.methodBuilder("getFieldIndexes")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(mapType)
            .addStatement("return FIELD_INDEXES")
            .build());
    }

    // ------------------------------------------------------------------ indexes / constraints / relationships

    private static void generateIndexes(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName indexModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "IndexModel");
        if (repo.indexes().isEmpty()) {
            GeneratorUtils.addFields(type,
                FieldSpec.builder(ArrayTypeName.of(indexModel), "INDEXES",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T[0]", indexModel),
                FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(List.class), indexModel),
                        "INDEXES_VIEW", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.of()", List.class));
        } else {
            CodeBlock.Builder arr = CodeBlock.builder().add("{\n");
            for (IndexModel idx : repo.indexes()) {
                arr.add("  new IndexModelImpl($S, new String[]{", idx.name());
                for (int i = 0; i < idx.fields().size(); i++) {
                    arr.add("$S", idx.fields().get(i));
                    if (i < idx.fields().size() - 1) arr.add(", ");
                }
                arr.add("}, IndexType.$L),\n", idx.type().name());
            }
            arr.add("}");
            GeneratorUtils.addFields(type,
                FieldSpec.builder(ArrayTypeName.of(indexModel), "INDEXES",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).initializer(arr.build()),
                FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(List.class), indexModel),
                        "INDEXES_VIEW", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.unmodifiableList($T.asList(INDEXES))",
                        ClassName.get("java.util", "Collections"),
                        ClassName.get("java.util", "Arrays")));
        }
    }

    private static void generateConstraints(TypeSpec.Builder type, RepositoryModel repo) {
        ClassName constraintModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "ConstraintModel");
        if (repo.constraints().isEmpty()) {
            GeneratorUtils.addFields(type,
                FieldSpec.builder(ArrayTypeName.of(constraintModel), "CONSTRAINTS",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T[0]", constraintModel),
                FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(List.class), constraintModel),
                        "CONSTRAINTS_VIEW", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.of()", List.class));
        } else {
            CodeBlock.Builder arr = CodeBlock.builder().add("{\n");
            for (ConstraintModel c : repo.constraints()) {
                arr.add("  new ConstraintModelImpl($S, new String[]{", c.name());
                for (int i = 0; i < c.fields().size(); i++) {
                    arr.add("$S", c.fields().get(i));
                    if (i < c.fields().size() - 1) arr.add(", ");
                }
                arr.add("}),\n");
            }
            arr.add("}");
            GeneratorUtils.addFields(type,
                FieldSpec.builder(ArrayTypeName.of(constraintModel), "CONSTRAINTS",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).initializer(arr.build()),
                FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(List.class), constraintModel),
                        "CONSTRAINTS_VIEW", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.unmodifiableList($T.asList(CONSTRAINTS))",
                        ClassName.get("java.util", "Collections"),
                        ClassName.get("java.util", "Arrays")));
        }
    }

    private static void generateRelationshipCaches(TypeSpec.Builder type, RepositoryModel repo,
                                                    ClassName entityClass) {
        ClassName fieldModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        ClassName map        = ClassName.get("java.util", "Map");
        ClassName collections = ClassName.get("java.util", "Collections");
        ParameterizedTypeName typeName = ParameterizedTypeName.get(fieldModel, entityClass);

        buildRelCache(type, repo, entityClass, typeName, map, collections,
            RelationshipKind.ONE_TO_ONE,  "ONE_TO_ONE_CACHE");
        buildRelCache(type, repo, entityClass, typeName, map, collections,
            RelationshipKind.ONE_TO_MANY, "ONE_TO_MANY_CACHE");
        buildRelCache(type, repo, entityClass, typeName, map, collections,
            RelationshipKind.MANY_TO_ONE, "MANY_TO_ONE_CACHE");
    }

    private static void buildRelCache(TypeSpec.Builder type, RepositoryModel repo,
                                       ClassName entityClass, ParameterizedTypeName typeName,
                                       ClassName map, ClassName collections,
                                       RelationshipKind kind, String fieldName) {
        CodeBlock.Builder init = CodeBlock.builder();
        boolean any = false;

        init.add("$T.unmodifiableMap($T.ofEntries(\n", collections, map);
        for (int i = 0; i < repo.fields().size(); i++) {
            FieldModel f = repo.fields().get(i);
            if (f.relationshipKind() == kind) {
                if (any) init.add(",\n");
                init.add("  $T.entry($S, ($T) FIELDS[$L])", map, f.name(), typeName, i);
                any = true;
            }
        }

        CodeBlock code = any
            ? init.add("\n))").build()
            : CodeBlock.of("$T.emptyMap()", collections);

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(map, ClassName.get(String.class), typeName),
                fieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(code).build());
    }

    private static void generateRelationshipModels(TypeSpec.Builder type, RepositoryModel repo,
                                                    ClassName entityClass, TypeName idClass) {
        ClassName relationshipModel = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipModel");
        ClassName relationshipKind  = ClassName.get("io.github.flameyossnowy.universal.api.meta", "RelationshipKind");
        ClassName fieldModel        = ClassName.get("io.github.flameyossnowy.universal.api.meta", "FieldModel");
        ClassName collectionKind    = ClassName.get("io.github.flameyossnowy.universal.api.factory", "CollectionKind");

        TypeName fieldModelType         = ParameterizedTypeName.get(fieldModel, entityClass);
        TypeName relationshipModelType  = ParameterizedTypeName.get(relationshipModel, entityClass, idClass);
        TypeName classWildcard          = ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class));
        TypeName relModelWild           = ParameterizedTypeName.get(relationshipModel, WildcardTypeName.subtypeOf(Object.class), WildcardTypeName.subtypeOf(Object.class));
        TypeName fieldModelWild         = ParameterizedTypeName.get(fieldModel, WildcardTypeName.subtypeOf(Object.class));

        if (repo.relationships().isEmpty()) {
            type.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), relationshipModelType),
                    "RELATIONSHIPS_VIEW", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.emptyList()", ClassName.get(Collections.class)).build());
            return;
        }

        List<String> relClassNames = new ArrayList<>(repo.relationships().size());

        for (RelationshipModel rel : repo.relationships()) {
            int fieldIndex = -1;
            for (int i = 0; i < repo.fields().size(); i++) {
                if (repo.fields().get(i).name().equals(rel.fieldName())) { fieldIndex = i; break; }
            }
            if (fieldIndex == -1) continue;

            String relClassName  = entityClass.simpleName() + "_" + rel.fieldName() + "_RelationshipModel";
            boolean isCollection = rel.relationshipKind() == RelationshipKind.ONE_TO_MANY;
            boolean isOwning     = rel.owning();
            String kindName      = switch (rel.collectionKind()) {
                case LIST  -> "LIST"; case SET   -> "SET";
                case QUEUE -> "QUEUE"; case DEQUE -> "DEQUE";
                default    -> "OTHER";
            };

            ClassName targetClass = ClassName.bestGuess(rel.targetEntityQualifiedName());
            final int fi = fieldIndex;

            TypeSpec.Builder relType = TypeSpec.classBuilder(relClassName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(relationshipModelType);

            relType.addMethod(simple("fieldName",        String.class,    rel.fieldName()));
            relType.addMethod(enumMethod("relationshipKind", relationshipKind, rel.relationshipKind().name()));
            relType.addMethod(bool("lazy",         rel.lazy()));
            relType.addMethod(bool("isCollection", isCollection));
            relType.addMethod(enumMethod("collectionKind", collectionKind, kindName));

            relType.addMethod(MethodSpec.methodBuilder("targetEntityType")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(classWildcard).addStatement("return $T.class", targetClass).build());
            relType.addMethod(MethodSpec.methodBuilder("getTargetRelationshipModel")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(relModelWild).addStatement("return null").build());
            relType.addMethod(MethodSpec.methodBuilder("getFieldModel")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(fieldModelType).addStatement("return ($T) FIELDS[$L]", fieldModelType, fi).build());
            relType.addMethod(MethodSpec.methodBuilder("getOwningFieldModel")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(fieldModelWild).addStatement("return null").build());
            relType.addMethod(bool("isOwning", isOwning));

            relType.addMethod(MethodSpec.methodBuilder("get")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(Object.class)).addParameter(entityClass, "entity")
                .addStatement("return getFieldModel().getValue(entity)").build());
            relType.addMethod(MethodSpec.methodBuilder("set")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID).addParameter(entityClass, "entity").addParameter(ClassName.get(Object.class), "value")
                .addStatement("getFieldModel().setValue(entity, value)").build());

            relType.addMethod(bool("cascadesInsert", false));
            relType.addMethod(bool("cascadesUpdate", false));
            relType.addMethod(bool("cascadesDelete", false));

            type.addType(relType.build());
            relClassNames.add(relClassName);
        }

        CodeBlock.Builder listInit = CodeBlock.builder().add("$T.of(\n", ClassName.get(List.class));
        for (int i = 0; i < relClassNames.size(); i++) {
            if (i != 0) listInit.add(",\n");
            listInit.add("  new $L()", relClassNames.get(i));
        }
        listInit.add("\n)");

        type.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(List.class), relationshipModelType),
                "RELATIONSHIPS_VIEW", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(listInit.build()).build());
    }

    // ------------------------------------------------------------------ tiny helpers

    private static MethodSpec simple(String name, Class<?> ret, String value) {
        return MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(ret).addStatement("return $S", value).build();
    }

    private static MethodSpec bool(String name, boolean value) {
        return MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN).addStatement("return $L", value).build();
    }

    private static MethodSpec enumMethod(String name, ClassName enumType, String enumValue) {
        return MethodSpec.methodBuilder(name)
            .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
            .returns(enumType).addStatement("return $T.$L", enumType, enumValue).build();
    }
}
