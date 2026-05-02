package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.GeneratedRepositoryFactory;
import io.github.flameyossnowy.universal.checker.FieldModel;
import io.github.flameyossnowy.universal.checker.GeneratorUtils;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single Responsibility: generate the {@code *_RelationshipLoader} class.
 *
 * <p>Delegates method-body generation to:
 * <ul>
 *   <li>{@link RelationshipMethodGenerator} – oneToOne / oneToMany / manyToOne</li>
 *   <li>{@link CollectionLoaderMethodGenerator} – loadList / loadSet / loadMap / loadArray</li>
 * </ul>
 */
public final class RelationshipLoaderGenerator {

    private final Filer                          filer;
    private final RelationshipMethodGenerator    relationshipMethods;
    private final CollectionLoaderMethodGenerator collectionMethods;

    public RelationshipLoaderGenerator(Filer filer) {
        this.filer               = filer;
        this.relationshipMethods  = new RelationshipMethodGenerator();
        this.collectionMethods    = new CollectionLoaderMethodGenerator();
    }

    public String generate(RepositoryModel repo, List<String> qualifiedNames) {
        List<FieldModel> pks = repo.primaryKeys();
        if (pks.isEmpty()) return null;

        TypeName   entityType = ClassName.get(repo.entityType());
        TypeName   idType     = TypeName.get(pks.getFirst().type());
        String     className  = repo.entitySimpleName() + "_RelationshipLoader";

        ParameterizedTypeName repoModelType = ParameterizedTypeName.get(
            ClassName.get("io.github.flameyossnowy.universal.api.meta", "RepositoryModel"),
            entityType, idType.box()
        );

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", "io.github.flameyossnowy.universal" +
                    ".checker.generator.UnifiedFactoryGenerator")
                .build())
            .addSuperinterface(TypeName.get(GeneratedRepositoryFactory.class))
            .addSuperinterface(ParameterizedTypeName.get(
                ClassName.get("io.github.flameyossnowy.universal.api.factory", "RelationshipLoader"),
                entityType, idType.box()));

        // register() method – called by ModelsBootstrap via ServiceLoader
        builder.addMethod(MethodSpec.methodBuilder("register")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addStatement(
                "io.github.flameyossnowy.universal.api.meta.GeneratedRelationshipLoaders"
                + ".<$T, $T>add($S, (handler, collectionHandler, model) -> new $L(handler, collectionHandler, model))",
                entityType, idType.box(), repo.tableName(), className)
            .build());

        addHandlerFields(builder, repoModelType);
        addCacheFields(builder, idType);

        builder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.handler", "RelationshipHandler"),  "handler")
            .addParameter(ClassName.get("io.github.flameyossnowy.universal.api.handler", "CollectionHandler"),    "collectionHandler")
            .addParameter(repoModelType, "repositoryModel")
            .addStatement("this.handler           = handler")
            .addStatement("this.collectionHandler = collectionHandler")
            .addStatement("this.repositoryModel   = repositoryModel")
            .build());

        // for ServiceLoader
        builder.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .build());

        builder.addMethod(relationshipMethods.generateOneToOneOwning(repo, idType));
        builder.addMethod(relationshipMethods.generateOneToOne(repo, idType, entityType));
        builder.addMethod(relationshipMethods.generateOneToMany(repo, idType));
        builder.addMethod(relationshipMethods.generateManyToOne(repo, idType, entityType));

        builder.addMethod(CollectionLoaderMethodGenerator.generateLoadList(repo, idType));
        builder.addMethod(CollectionLoaderMethodGenerator.generateLoadSet(repo, idType));
        builder.addMethod(CollectionLoaderMethodGenerator.generateLoadMap(repo, idType));
        builder.addMethod(CollectionLoaderMethodGenerator.generateLoadArray(repo, idType));

        builder.addMethod(invalidateMethod(idType));
        builder.addMethod(clearMethod());

        String qualifiedName = GeneratorUtils.qualifiedName(repo.packageName(), className);
        qualifiedNames.add(qualifiedName);
        GeneratorUtils.write(repo.packageName(), builder.build(), filer);
        return qualifiedName;
    }

    // ------------------------------------------------------------------ helpers

    private static void addHandlerFields(TypeSpec.Builder b, TypeName repoModelType) {
        b.addField(FieldSpec.builder(
                ClassName.get("io.github.flameyossnowy.universal.api.handler", "RelationshipHandler"),
                "handler", Modifier.PRIVATE).build());
        b.addField(FieldSpec.builder(
                ClassName.get("io.github.flameyossnowy.universal.api.handler", "CollectionHandler"),
                "collectionHandler", Modifier.PRIVATE).build());
        b.addField(FieldSpec.builder(repoModelType,
                "repositoryModel", Modifier.PRIVATE).build());
    }

    private static void addCacheFields(TypeSpec.Builder b, TypeName idType) {
        TypeName idBoxed = idType.box();

        b.addField(FieldSpec.builder(Object.class, "NULL_SENTINEL",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("new Object()")
            .build());

        // Map<ID, Map<String, Object>>
        TypeName singleCache = ParameterizedTypeName.get(ClassName.get(Map.class),
            idBoxed, ParameterizedTypeName.get(ClassName.get(Map.class),
                ClassName.get(String.class), ClassName.get(Object.class)));

        // Map<ID, Map<String, Collection<Object>>>
        TypeName collectionCache = ParameterizedTypeName.get(ClassName.get(Map.class),
            idBoxed, ParameterizedTypeName.get(ClassName.get(Map.class),
                ClassName.get(String.class),
                ParameterizedTypeName.get(ClassName.get(Collection.class), ClassName.get(Object.class))));

        // Map<ID, Map<String, Map<Object,Object>>>
        TypeName mapCache = ParameterizedTypeName.get(ClassName.get(Map.class),
            idBoxed, ParameterizedTypeName.get(ClassName.get(Map.class),
                ClassName.get(String.class),
                ParameterizedTypeName.get(ClassName.get(Map.class),
                    ClassName.get(Object.class), ClassName.get(Object.class))));

        // Map<ID, Map<String, Object[]>>
        TypeName arrayCache = ParameterizedTypeName.get(ClassName.get(Map.class),
            idBoxed, ParameterizedTypeName.get(ClassName.get(Map.class),
                ClassName.get(String.class), ArrayTypeName.of(ClassName.get(Object.class))));

        cacheField(b, "oneToOneCache",  singleCache);
        cacheField(b, "oneToManyCache", collectionCache);
        cacheField(b, "manyToOneCache", singleCache);
        cacheField(b, "listCache",      collectionCache);
        cacheField(b, "setCache",       collectionCache);
        cacheField(b, "mapCache",       mapCache);
        cacheField(b, "arrayCache",     arrayCache);
    }

    private static void cacheField(TypeSpec.Builder b, String name, TypeName typeName) {
        b.addField(FieldSpec.builder(typeName, name, Modifier.PRIVATE, Modifier.FINAL)
            .initializer("new $T<>()", ConcurrentHashMap.class)
            .build());
    }

    private static MethodSpec invalidateMethod(TypeName idType) {
        return MethodSpec.methodBuilder("invalidateRelationshipsForId")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(idType.box(), "id")
            .addStatement("oneToOneCache.remove(id)")
            .addStatement("oneToManyCache.remove(id)")
            .addStatement("manyToOneCache.remove(id)")
            .addStatement("listCache.remove(id)")
            .addStatement("setCache.remove(id)")
            .addStatement("mapCache.remove(id)")
            .addStatement("arrayCache.remove(id)")
            .build();
    }

    private static MethodSpec clearMethod() {
        return MethodSpec.methodBuilder("clear")
            .addModifiers(Modifier.PUBLIC)
            .addStatement("oneToOneCache.clear()")
            .addStatement("oneToManyCache.clear()")
            .addStatement("manyToOneCache.clear()")
            .addStatement("listCache.clear()")
            .addStatement("setCache.clear()")
            .addStatement("mapCache.clear()")
            .addStatement("arrayCache.clear()")
            .build();
    }
}
