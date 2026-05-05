package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.GeneratedRepositoryFactory;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.checker.FieldModel;
import io.github.flameyossnowy.universal.checker.GeneratorUtils;
import io.github.flameyossnowy.universal.checker.RelationshipModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

/**
 * Single Responsibility: generate the {@code *_ObjectModel} class.
 *
 * <p>Delegates insert-method generation to:
 * <ul>
 *   <li>{@link InsertEntityGenerator}</li>
 *   <li>{@link InsertCollectionEntitiesGenerator}</li>
 * </ul>
 */
public final class ObjectModelGenerator {

    private final Filer                           filer;
    private final Types                           types;
    private final InsertEntityGenerator           insertEntity;
    private final InsertCollectionEntitiesGenerator insertCollections;
    private final LazyProxyGenerator              lazyProxy;
    private final Elements elements;

    public ObjectModelGenerator(Filer filer, Types types, Elements elements,
                                 javax.annotation.processing.Messager messager) {
        this.filer             = filer;
        this.types             = types;
        this.elements          = elements;
        this.insertEntity      = new InsertEntityGenerator(types, elements, messager);
        this.insertCollections = new InsertCollectionEntitiesGenerator();
        this.lazyProxy         = new LazyProxyGenerator(elements, filer);
    }

    // ------------------------------------------------------------------

    public String generate(RepositoryModel repo, List<String> qualifiedNames) {
        ClassName entityType = ClassName.bestGuess(repo.entityQualifiedName());

        List<FieldModel> pks = repo.primaryKeys();
        TypeName idType = pks.isEmpty()
            ? TypeName.get(Object.class)
            : TypeName.get(pks.getFirst().type()).box();

        // Generate lazy proxies first (they are separate files)
        for (RelationshipModel rel : repo.relationships()) {
            if (!rel.lazy()) continue;
            switch (rel.relationshipKind()) {
                case ONE_TO_ONE, MANY_TO_ONE -> lazyProxy.generateEntityProxy(repo, rel);
                case ONE_TO_MANY             -> lazyProxy.generateCollectionProxy(repo, rel);
            }
        }

        String className = repo.entitySimpleName() + "_ObjectModel";

        ParameterizedTypeName repoModelType = ParameterizedTypeName.get(
            ClassName.get(io.github.flameyossnowy.universal.api.meta.RepositoryModel.class),
            entityType, idType.box());

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal.checker.generator.UnifiedFactoryGenerator")
                .build())
            .addField(FieldSpec.builder(repoModelType, "repositoryModel", Modifier.PRIVATE).build())
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(ObjectModel.class), entityType, idType.box()))
            .addSuperinterface(TypeName.get(GeneratedRepositoryFactory.class))
            .addMethod(MethodSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder(repoModelType, "repositoryModel").build())
                .addStatement("this.repositoryModel = repositoryModel")
                .addModifiers(Modifier.PUBLIC)
                .build())
            // for ServiceLoader
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .build())
            .addMethod(generateConstruct(repo, entityType))
            .addMethod(generatePopulateRelationships(repo, entityType, idType))
            .addMethod(insertEntity.generate(repo, entityType));

        builder.addMethod(InsertCollectionEntitiesGenerator.generate(repo, entityType, idType))
            .addMethod(generateGetId(repo, entityType, idType))
            .addMethod(generateGetFieldValue(repo, entityType))
            .addMethod(generateGetIdType(idType))
            .addMethod(generateGetEntityType(entityType));

        // register() method – called by ModelsBootstrap via ServiceLoader
        builder.addMethod(MethodSpec.methodBuilder("register")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement(
                "io.github.flameyossnowy.universal.api.meta.GeneratedObjectFactories"
                + ".add($S, $T.class, $T.class, (model) -> new $L(model))",
                repo.tableName(), entityType, idType.box(), className)
            .build());

        String qualifiedName = GeneratorUtils.qualifiedName(repo.packageName(), className);
        qualifiedNames.add(qualifiedName);
        GeneratorUtils.write(repo.packageName(), builder.build(), filer);
        return qualifiedName;
    }

    // ------------------------------------------------------------------ private methods

    private static MethodSpec generateGetId(RepositoryModel repo, ClassName entityType, TypeName idType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("getId")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(idType)
            .addParameter(entityType, "entity");

        if (repo.primaryKeys().isEmpty()) {
            return m.addStatement("return null").build();
        }
        return m.addStatement("return entity.$L()", repo.primaryKeys().getFirst().getterName()).build();
    }

    private static MethodSpec generateGetFieldValue(RepositoryModel repo, ClassName entityType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("getFieldValue")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(entityType, "entity")
            .addParameter(String.class, "fieldName");

        StringBuilder sb = new StringBuilder(64);
        sb.append("return switch (fieldName) {\n");

        for (FieldModel field : repo.fields()) {
            sb.append("case \"")
                .append(field.name())
                .append("\" -> entity.")
                .append(field.getterName())
                .append("();\n");
        }

        sb.append("default -> throw new IllegalArgumentException(\"Unknown field: \" + fieldName);\n");
        sb.append("}");

        m.addStatement(sb.toString());

        return m.build();
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

    private MethodSpec generateConstruct(RepositoryModel repo, ClassName entityType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("construct")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(entityType)
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.factory", "ValueReader"), "r");

        if (repo.isRecord()) {
            return m.addStatement("throw new $T($S)", UnsupportedOperationException.class,
                "Records are constructed via canonical constructor").build();
        }

        m.addStatement("$T entity = new $T()", entityType, entityType);
        int index = 0;
        for (FieldModel field : repo.fields()) {
            if (field.relationship()) continue;
            if (field.autoIncrement()) {
                m.addStatement("entity.$L(($T) r.getId())", field.setterName(), ClassName.get(field.type()));
                continue;
            }
            m.addStatement("entity.$L(($T) r.read($L))", field.setterName(),
                ClassName.get(field.type()), index++);
        }
        m.addStatement("return entity");
        return m.build();
    }

    private static MethodSpec generatePopulateRelationships(RepositoryModel repo,
                                                            ClassName entityType,
                                                            TypeName idClass) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("populateRelationships")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(entityType, "entity")
            .addParameter(idClass, "id")
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.factory", "RelationshipLoader"), "loader")
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.factory", "ValueReader"), "r");

        // Count regular participating fields to find where owning FK indices start
        int fkIndex = 0;
        for (FieldModel field : repo.fields()) {
            if (field.participatesInConstruction()) fkIndex++;
        }

        int owningFkIdx = fkIndex;
        for (RelationshipModel rel : repo.relationships()) {
            if (rel.relationshipKind() == RelationshipKind.ONE_TO_ONE
                && (rel.mappedBy() == null || rel.mappedBy().isBlank())) {
                m.addStatement("entity.$L(($T) loader.oneToOneOwning($S, r.read($L), $T.class))",
                    rel.setterName(),
                    rel.targetTypeName(),
                    rel.columnName(),
                    owningFkIdx,
                    rel.targetTypeName());
                owningFkIdx++;
            } else {
                String call = buildCallSyntax(rel, repo);
                Object[] args = rel.relationshipKind() == RelationshipKind.ONE_TO_MANY
                    ? (rel.lazy()
                    ? new Object[]{ rel.setterName(), rel.fieldType() }
                    : new Object[]{ rel.setterName(), rel.fieldType(), rel.targetTypeName() })
                    : new Object[]{ rel.setterName(), rel.targetTypeName(), rel.targetTypeName() };
                m.addStatement("entity.$L(($T) " + call + ")", args);
            }
        }
        return m.build();
    }

    private static String buildCallSyntax(RelationshipModel rel, RepositoryModel repo) {
        String loaderMethod = switch (rel.relationshipKind()) {
            case ONE_TO_ONE  -> "oneToOne";
            case ONE_TO_MANY -> "oneToMany";
            case MANY_TO_ONE -> "manyToOne";
        };

        if (!rel.lazy()) {
            return switch (rel.relationshipKind()) {
                case ONE_TO_MANY -> "loader.oneToMany(\"" + rel.columnName() + "\", id, $T.class)";
                case MANY_TO_ONE -> "loader.manyToOne(\"" + rel.columnName() + "\", id, entity, $T.class)";
                case ONE_TO_ONE -> {
                    // If mappedBy is set, this is the inverse side -> use oneToOne (queries by back-reference)
                    // If mappedBy is blank, this side holds the FK column -> use oneToOneOwning
                    if (rel.mappedBy() == null || rel.mappedBy().isBlank()) {
                        yield "loader.oneToOneOwning(\"" + rel.columnName() + "\", r.read(" + /* fkIdx */ "fkIdx" + "), $T.class)";
                    } else {
                        yield "loader.oneToOne(\"" + rel.columnName() + "\", id, entity, $T.class)";
                    }
                }
            };
        }
        return rel.relationshipKind() == RelationshipKind.ONE_TO_MANY
            ? "new " + LazyProxyGenerator.collectionProxyName(repo, rel) + "(id, loader)"
            : "new " + LazyProxyGenerator.entityProxyName(repo, rel)     + "(id, loader, $T.class)";
    }
}
