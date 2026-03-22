package io.github.flameyossnowy.universal.checker.generator;

import com.squareup.javapoet.*;
import io.github.flameyossnowy.universal.api.GeneratedRepositoryFactory;
import io.github.flameyossnowy.universal.api.cache.BatchLoaderRegistry;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.LazyBatchContext;
import io.github.flameyossnowy.universal.api.handler.LazyBatchKey;
import io.github.flameyossnowy.universal.api.handler.LazyEntityRegistry;
import io.github.flameyossnowy.universal.checker.CollectionKind;
import io.github.flameyossnowy.universal.checker.GeneratorUtils;
import io.github.flameyossnowy.universal.checker.RelationshipModel;
import io.github.flameyossnowy.universal.checker.RepositoryModel;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Single Responsibility: generate lazy-loading proxy classes for entity and
 * collection relationships.
 */
public final class LazyProxyGenerator {

    private final Elements elements;
    private final Filer    filer;

    public LazyProxyGenerator(Elements elements, Filer filer) {
        this.elements = elements;
        this.filer    = filer;
    }

    // ------------------------------------------------------------------

    public void generateEntityProxy(RepositoryModel repo, RelationshipModel rel) {
        String proxyName  = entityProxyName(repo, rel);
        TypeName idType   = ClassName.get(repo.primaryKeys().getFirst().type()).box();
        ClassName target  = ClassName.bestGuess(rel.targetEntityQualifiedName());

        ParameterizedTypeName loaderType = ParameterizedTypeName.get(
            ClassName.get(RelationshipLoader.class),
            ClassName.get(repo.entityType()),
            idType);

        TypeSpec.Builder type = TypeSpec.classBuilder(proxyName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(target)
            .addSuperinterface(TypeName.get(GeneratedRepositoryFactory.class))
            .addAnnotation(generatedAnnotation("Lazy loading proxy for " + rel.fieldName()));

        type.addField(idType,      "ownerId", Modifier.PRIVATE, Modifier.FINAL);
        type.addField(loaderType,  "loader",  Modifier.PRIVATE, Modifier.FINAL);
        type.addField(target,      "value",   Modifier.PRIVATE);
        type.addField(boolean.class, "loaded", Modifier.PRIVATE);

        type.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(idType,     "ownerId")
            .addParameter(loaderType, "loader")
            .addStatement("this.ownerId = ownerId")
            .addStatement("this.loader  = loader")
            .build());

        type.addMethod(MethodSpec.methodBuilder("load")
            .addModifiers(Modifier.PRIVATE)
            .returns(target)
            .beginControlFlow("if (!loaded)")
            .addStatement("$T resolved = ($T) $T.resolve($L, ownerId)",
                target, target, LazyEntityRegistry.class, batchKey(repo, rel))
            .beginControlFlow("if (resolved != null)")
            .addStatement("value  = resolved")
            .addStatement("loaded = true")
            .addStatement("return value")
            .endControlFlow()
            .addStatement("$T.<$T>current().register($L, ownerId)",
                LazyBatchContext.class, idType, batchKey(repo, rel))
            .addStatement("$T.markPending()", BatchLoaderRegistry.class)
            .endControlFlow()
            .addStatement("return value")
            .build());

        overrideEntityMethods(type, target);
        GeneratorUtils.write(repo.packageName(), type.build(), filer);
    }

    public void generateCollectionProxy(RepositoryModel repo, RelationshipModel rel) {
        String   proxyName   = collectionProxyName(repo, rel);
        TypeName idType      = ClassName.get(repo.primaryKeys().getFirst().type()).box();
        ClassName elementType = ClassName.bestGuess(rel.targetEntityQualifiedName());
        CollectionKind kind  = rel.collectionKind();

        ClassName   collectionRaw;
        ClassName   abstractBase;
        ClassName   emptyFactory;
        TypeName    collectionType;
        TypeName    abstractType;

        switch (kind) {
            case LIST -> {
                collectionRaw  = ClassName.get(List.class);
                abstractBase   = ClassName.get("java.util", "AbstractList");
                emptyFactory   = ClassName.get(List.class);
                collectionType = ParameterizedTypeName.get(collectionRaw, elementType);
                abstractType   = ParameterizedTypeName.get(abstractBase,  elementType);
            }
            case SET -> {
                collectionRaw  = ClassName.get(Set.class);
                abstractBase   = ClassName.get("java.util", "AbstractSet");
                emptyFactory   = ClassName.get(Set.class);
                collectionType = ParameterizedTypeName.get(collectionRaw, elementType);
                abstractType   = ParameterizedTypeName.get(abstractBase,  elementType);
            }
            case MAP -> throw new UnsupportedOperationException(
                "MAP relationships require a separate proxy implementation");
            default -> {
                collectionRaw  = ClassName.get(Collection.class);
                abstractBase   = ClassName.get("java.util", "AbstractCollection");
                emptyFactory   = ClassName.get(List.class);
                collectionType = ParameterizedTypeName.get(collectionRaw, elementType);
                abstractType   = ParameterizedTypeName.get(abstractBase,  elementType);
            }
        }

        ParameterizedTypeName loaderType = ParameterizedTypeName.get(
            ClassName.get(RelationshipLoader.class),
            ClassName.get(repo.entityType()),
            idType);

        TypeSpec.Builder type = TypeSpec.classBuilder(proxyName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(abstractType)
            .addAnnotation(generatedAnnotation("Lazy collection loading proxy for " + rel.fieldName()));

        type.addField(idType,          "ownerId", Modifier.PRIVATE);
        type.addField(loaderType,      "loader",  Modifier.PRIVATE);
        type.addField(collectionType,  "value",   Modifier.PRIVATE);
        type.addField(boolean.class,   "loaded",  Modifier.PRIVATE);

        type.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(idType,     "ownerId")
            .addParameter(loaderType, "loader")
            .addStatement("this.ownerId = ownerId")
            .addStatement("this.loader  = loader")
            .build());

        // for ServiceLoader
        type.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .build());

        type.addMethod(MethodSpec.methodBuilder("load")
            .addModifiers(Modifier.PRIVATE)
            .returns(collectionType)
            .beginControlFlow("if (!loaded)")
            .addStatement("$T resolved = ($T) $T.resolve($L, ownerId)",
                collectionType, collectionType, LazyEntityRegistry.class, batchKey(repo, rel))
            .beginControlFlow("if (resolved != null)")
            .addStatement("value  = resolved")
            .addStatement("loaded = true")
            .addStatement("return value")
            .endControlFlow()
            .addStatement("$T.<$T>current().register($L, ownerId)",
                LazyBatchContext.class, idType, batchKey(repo, rel))
            .addStatement("$T.markPending()", BatchLoaderRegistry.class)
            .endControlFlow()
            .beginControlFlow("if (value == null)")
            .addStatement("value = $T.of()", emptyFactory)
            .endControlFlow()
            .addStatement("return value")
            .build());

        // Abstract-method overrides vary by collection kind
        switch (kind) {
            case LIST -> {
                type.addMethod(MethodSpec.methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(elementType)
                    .addParameter(TypeName.INT, "index")
                    .addStatement("return load().get(index)")
                    .build());
                type.addMethod(sizeMethod());
            }
            case SET -> {
                type.addMethod(iteratorMethod(elementType));
                type.addMethod(sizeMethod());
            }
            default -> {
                type.addMethod(sizeMethod());
                type.addMethod(iteratorMethod(elementType));
            }
        }

        GeneratorUtils.write(repo.packageName(), type.build(), filer);
    }

    // ------------------------------------------------------------------ helpers

    private void overrideEntityMethods(TypeSpec.Builder type, ClassName target) {
        TypeElement element = elements.getTypeElement(target.canonicalName());
        for (Element e : element.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) continue;
            ExecutableElement m = (ExecutableElement) e;
            if (m.getModifiers().contains(Modifier.STATIC))  continue;
            if (m.getModifiers().contains(Modifier.FINAL))   continue;
            if (m.getModifiers().contains(Modifier.PRIVATE)) continue;
            if (m.getSimpleName().contentEquals("<init>"))   continue;

            MethodSpec.Builder override = MethodSpec.overriding(m);
            StringBuilder args = new StringBuilder();
            boolean first = true;
            for (VariableElement p : m.getParameters()) {
                if (!first) args.append(", ");
                args.append(p.getSimpleName());
                first = false;
            }

            if (m.getReturnType().getKind() == TypeKind.VOID) {
                override.addStatement("load().$L($L)", m.getSimpleName(), args);
            } else {
                override.addStatement("return load().$L($L)", m.getSimpleName(), args);
            }
            type.addMethod(override.build());
        }
    }

    private static MethodSpec sizeMethod() {
        return MethodSpec.methodBuilder("size")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("return load().size()")
            .build();
    }

    private static MethodSpec iteratorMethod(ClassName elementType) {
        return MethodSpec.methodBuilder("iterator")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Iterator.class), elementType))
            .addStatement("return load().iterator()")
            .build();
    }

    private static CodeBlock batchKey(RepositoryModel repo, RelationshipModel rel) {
        return CodeBlock.of("new $T($S, $S)", LazyBatchKey.class,
            repo.tableName(), rel.fieldName());
    }

    private static AnnotationSpec generatedAnnotation(String comment) {
        return AnnotationSpec.builder(Generated.class)
            .addMember("value",    "$S", "io.github.flameyossnowy.universal.checker.generator.UnifiedFactoryGenerator")
            .addMember("comments", "$S", comment)
            .build();
    }

    // ------------------------------------------------------------------ names (public for callers)

    public static String entityProxyName(RepositoryModel repo, RelationshipModel rel) {
        return repo.entitySimpleName() + "_" + rel.fieldName() + "_LazyProxy";
    }

    public static String collectionProxyName(RepositoryModel repo, RelationshipModel rel) {
        return repo.entitySimpleName() + "_" + rel.fieldName() + "_LazyCollectionProxy";
    }
}
