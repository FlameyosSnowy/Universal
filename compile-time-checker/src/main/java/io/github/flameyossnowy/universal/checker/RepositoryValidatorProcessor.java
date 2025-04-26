package io.github.flameyossnowy.universal.checker;

import io.github.flameyossnowy.universal.api.annotations.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "io.github.flameyossnowy.universal.api.annotations.Repository",
        "io.github.flameyossnowy.universal.api.annotations.Cacheable",
        "io.github.flameyossnowy.universal.api.annotations.GlobalCacheable",
        "io.github.flameyossnowy.universal.api.annotations.ManyToOne",
        "io.github.flameyossnowy.universal.api.annotations.OneToMany",
        "io.github.flameyossnowy.universal.api.annotations.OneToOne",
        "io.github.flameyossnowy.universal.api.annotations.Constraint",
        "io.github.flameyossnowy.universal.api.annotations.Index"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17) // or your target version
public class RepositoryValidatorProcessor extends AbstractProcessor {
    private Messager messager;

    private final Map<String, Element> repositoryNames = new HashMap<>(5);

    private static final List<Class<? extends Annotation>> FIELD_ANNOTATIONS = List.of(
            ManyToOne.class,
            OneToMany.class,
            OneToOne.class,
            Unique.class,
            NonNull.class,
            Now.class,
            Id.class,
            AutoIncrement.class,
            Named.class,
            OnDelete.class,
            OnUpdate.class,
            Constraint.class,
            DefaultValue.class,
            DefaultValueProvider.class
    );

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (TypeElement element : set) {
            Repository repository = element.getAnnotation(Repository.class);
            if (repository == null) continue;

            if (element.getModifiers().contains(Modifier.ABSTRACT)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot be abstract", element);
                continue;
            }

            ElementKind kind = element.getKind();
            if (kind == ElementKind.ENUM) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot be an enum", element);
                continue;
            }

            if (kind == ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot be an interface", element);
                continue;
            }

            if (repository.name().isBlank()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " name cannot be empty", element);
                continue;
            }

            checkIndexAndConstraintReferences(element);

            List<? extends Element> enclosedElements = element.getEnclosedElements();
            boolean hasNoArgConstructor = false;

            for (Element enclosedElement : enclosedElements) {
                ElementKind elementKind = enclosedElement.getKind();
                if (elementKind == ElementKind.CONSTRUCTOR && ((ExecutableElement) enclosedElement).getParameters().isEmpty()) {
                    hasNoArgConstructor = true;
                }
                if (elementKind == ElementKind.FIELD) handleField(element, (VariableElement) enclosedElement);

            }

            if (!hasNoArgConstructor) messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " must have a no-arg constructor", element);

            String name = repository.name();

            // Check for duplicate
            if (repositoryNames.containsKey(name)) {
                Element existing = repositoryNames.get(name);
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Duplicate @Repository name: '" + name + "' already used by " + existing.getSimpleName(),
                        element
                );
            } else {
                repositoryNames.put(name, element);
            }
        }
        return false;
    }

    private void checkIndexAndConstraintReferences(@NotNull TypeElement classElement) {
        Set<String> fieldNames = classElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .map((element) -> element.getSimpleName().toString())
                .collect(Collectors.toSet());

        for (Element enclosed : classElement.getEnclosedElements()) {
            for (Index index : enclosed.getAnnotationsByType(Index.class)) {
                validateColumns("Index", index.fields(), fieldNames, enclosed, classElement);
            }

            for (Constraint constraint : enclosed.getAnnotationsByType(Constraint.class)) {
                validateColumns("Constraint", constraint.fields(), fieldNames, enclosed, classElement);
            }
        }
    }

    private void validateColumns(String kind, String[] columns, Set<String> validFields, Element target, TypeElement classElement) {
        for (String column : columns) {
            if (!validFields.contains(column)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@" + kind + " on " + classElement.getSimpleName() +
                                " refers to unknown field: '" + column + "'",
                        target);
            }
        }
    }


    private void handleField(Element element, @NotNull VariableElement enclosedElement) {
        Set<Modifier> modifiers = enclosedElement.getModifiers();

        checkRelationshipFieldAnnotations(element, enclosedElement);

        if (modifiers.contains(Modifier.STATIC)) {
            // if it's a static field, but it has a field annotation from FIELD_ANNOTATIONS
            checkStaticFieldAnnotations(element, enclosedElement);
            return;
        }

        if (modifiers.contains(Modifier.FINAL)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot have a final field like " + enclosedElement.getSimpleName() + " (unless as static final)", element);
        }

        if (modifiers.contains(Modifier.TRANSIENT)) {
            // if it's a static field, but it has a field annotation from FIELD_ANNOTATIONS
            checkTransientFieldAnnotations(element, enclosedElement);
            return;
        }

        checkRawCollections(enclosedElement);
    }

    private void checkRelationshipFieldAnnotations(Element element, VariableElement enclosedElement) {
        OneToOne oneToOne = enclosedElement.getAnnotation(OneToOne.class);
        OneToMany oneToMany = enclosedElement.getAnnotation(OneToMany.class);
        ManyToOne manyToOne = enclosedElement.getAnnotation(ManyToOne.class);

        int relationshipCount = 0;
        if (oneToOne != null) relationshipCount++;
        if (oneToMany != null) relationshipCount++;
        if (manyToOne != null) relationshipCount++;

        if (relationshipCount > 1) {
            // If multiple relationship annotations are present, throw an error
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                            + " is annotated with multiple relationship annotations (@OneToOne, @OneToMany, @ManyToOne). Only one relationship annotation is allowed per field.",
                    enclosedElement);
            return; // No need to continue further processing
        }

        if (oneToOne != null) {
            TypeMirror fieldType = enclosedElement.asType();

            // Check if the field type is a class
            if (fieldType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) fieldType;
                Element typeElement = declaredType.asElement();
                if (typeElement.getAnnotation(Repository.class) == null) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                    + " is annotated with @OneToOne, but the referenced type '" + typeElement.getSimpleName() + "' is not annotated with @Repository.",
                            enclosedElement);
                }
            }
        }

        if (manyToOne != null) {
            TypeMirror fieldType = enclosedElement.asType();

            // Check if the field type is a class
            if (fieldType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) fieldType;
                Element typeElement = declaredType.asElement();
                if (typeElement.getAnnotation(Repository.class) == null) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                    + " is annotated with @ManyToOne, but the referenced type '" + typeElement.getSimpleName() + "' is not annotated with @Repository.",
                            enclosedElement);
                }
            }
        }

        if (oneToMany != null) {
            Class<?> mappedBy = oneToMany.mappedBy();

            // Check if the field type is a class
            if (mappedBy.getAnnotation(Repository.class) == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                + " is annotated with @OneToMany, but the referenced type '" + mappedBy.getSimpleName() + "' is not annotated with @Repository.",
                        enclosedElement);
            }
        }
    }

    private void checkRawCollections(@NotNull VariableElement classElement) {
        TypeMirror type = classElement.asType();
        if (type.getKind() != TypeKind.DECLARED) return;

        if (!(type instanceof DeclaredType declaredType)) return;
        if (!(declaredType.asElement() instanceof TypeElement)) return;

        if (declaredType.getTypeArguments().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + classElement.getSimpleName() + "' in " + classElement.getSimpleName()
                            + " is a raw Collection. Use generics, e.g., List<String>.",
                    classElement);
        }
    }

    private void checkStaticFieldAnnotations(Element element, VariableElement enclosedElement) {
        for (Class<? extends Annotation> annotation : FIELD_ANNOTATIONS) {
            if (enclosedElement.getAnnotation(annotation) != null) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot have a static field annotated with @" + annotation.getSimpleName() + " or from the following list: " + FIELD_ANNOTATIONS, element);
            }
        }
    }

    private void checkTransientFieldAnnotations(Element element, VariableElement enclosedElement) {
        for (Class<? extends Annotation> annotation : FIELD_ANNOTATIONS) {
            if (enclosedElement.getAnnotation(annotation) != null) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot have a transient field annotated with @" + annotation.getSimpleName() + " or from the following list: " + FIELD_ANNOTATIONS, element);
            }
        }
    }
}
