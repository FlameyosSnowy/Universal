package io.github.flameyossnowy.universal.checker;

import io.github.flameyossnowy.universal.api.annotations.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

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

    //private boolean enableOptimizationHints;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        /*this.enableOptimizationHints = Boolean.parseBoolean(processingEnv.getOptions()
                .getOrDefault("repositoryChecker.enableOptimizationHints", "false"));*/
    }


    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("io.github.flameyossnowy.universal.api.annotations.Repository",
                "io.github.flameyossnowy.universal.api.annotations.Cacheable",
                "io.github.flameyossnowy.universal.api.annotations.GlobalCacheable",
                "io.github.flameyossnowy.universal.api.annotations.ManyToOne",
                "io.github.flameyossnowy.universal.api.annotations.OneToMany",
                "io.github.flameyossnowy.universal.api.annotations.OneToOne",
                "io.github.flameyossnowy.universal.api.annotations.Constraint",
                "io.github.flameyossnowy.universal.api.annotations.Index");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public boolean process(@NotNull Set<? extends TypeElement> set, @NotNull RoundEnvironment roundEnvironment) {
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(Repository.class);
        if (elements.isEmpty()) return false;

        for (Element element : elements) {
            if (!(element instanceof TypeElement typeElement)) continue;
            ElementKind kind = element.getKind();

            handleRepository(typeElement, kind);

            List<? extends Element> enclosedElements = element.getEnclosedElements();
            boolean hasNoArgConstructor = false;
            boolean hasIdField = false;
            boolean hasRelationshipField = false;

            for (Element enclosedElement : enclosedElements) {
                ElementKind elementKind = enclosedElement.getKind();
                if (elementKind == ElementKind.CONSTRUCTOR && ((ExecutableElement) enclosedElement).getParameters().isEmpty()) {
                    hasNoArgConstructor = true;
                }
                if (elementKind != ElementKind.FIELD) continue;

                // Check if field has @Id
                if (enclosedElement.getAnnotation(Id.class) != null) {
                    hasIdField = true;
                }

                // Check if field has relationship annotations
                if (enclosedElement.getAnnotation(OneToOne.class) != null ||
                        enclosedElement.getAnnotation(OneToMany.class) != null) {
                    hasRelationshipField = true;
                }


                handleField(element, (VariableElement) enclosedElement);
            }

            if (hasRelationshipField && !hasIdField) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@Repository " + element.getSimpleName() + " has relationship fields (@OneToOne, @OneToMany) but no field annotated with @Id. " +
                                "Repositories with relationships must have an @Id field.",
                        element);
            }

            if (!hasNoArgConstructor) messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " must have a no-arg constructor", element);
        }
        return true;
    }

    private void handleRepository(TypeElement element, ElementKind kind) {
        // we check if it's interface so we don't execute `return`
        // just to slap the user when we tell it interfaces aren't allowed ;)
        if (!kind.isClass() || !kind.isInterface()) return;

        Repository repository = element.getAnnotation(Repository.class);
        if (repository == null) return;

        if (repositoryNames.containsKey(repository.name())) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " repository name must be unique, as it's being used by " + repositoryNames.get(repository.name()).getSimpleName(), element);
            return;
        }

        if (element.getModifiers().contains(Modifier.ABSTRACT)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot be abstract", element);
            return;
        }

        if (kind == ElementKind.ENUM) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot be an enum", element);
            return;
        }

        // womp womp
        if (kind == ElementKind.INTERFACE) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " cannot be an interface", element);
            return;
        }

        if (repository.name().isBlank()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Repository " + element.getSimpleName() + " name cannot be empty", element);
            return;
        }

        repositoryNames.put(repository.name(), element);

        checkIndexAndConstraintReferences(element);
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
            if (validFields.contains(column)) continue;
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@" + kind + " on " + classElement.getSimpleName() + " refers to unknown field: '" + column + "'",
                    target);

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

        checkRawTypes(enclosedElement);
    }

    private static boolean isCollectionType(Element element) {
        if (!(element instanceof VariableElement variableElement)) return false;

        TypeMirror fieldType = variableElement.asType();
        if (fieldType.getKind() != TypeKind.DECLARED) return false;

        DeclaredType declaredType = (DeclaredType) fieldType;
        Element typeElement = declaredType.asElement();

        // Check if the field type implements Collection (List, Set, etc.)
        String qualifiedName = ((TypeElement) typeElement).getQualifiedName().toString();

        // Check for direct collection types like List, Set, etc.
        return qualifiedName.equals("java.util.List") || qualifiedName.equals("java.util.Set") || qualifiedName.equals("java.util.Map");
    }

    private void checkRelationshipFieldAnnotations(Element element, @NotNull VariableElement enclosedElement) {
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

            if (isCollectionType(enclosedElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                + " is annotated with @OneToOne, but the field type is a collection type.",
                        enclosedElement);

            } else checkDeclared(fieldType, enclosedElement, element, " is annotated with @OneToOne, but the referenced type '");
        }

        if (manyToOne != null) {
            TypeMirror fieldType = enclosedElement.asType();

            // Check if the field type is a class
            if (isCollectionType(enclosedElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                + " is annotated with @ManyToOne, but the field type is a collection type.",
                        enclosedElement);

            } else checkDeclared(fieldType, enclosedElement, element, " is annotated with @ManyToOne, but the referenced type '");
        }

        if (oneToMany != null) {
            TypeMirror mappedBy = getMappedBy(oneToMany);

            if (!isCollectionType(enclosedElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                + " is annotated with @OneToMany, but the field type is not a collection type.",
                        enclosedElement);

            } else if (mappedBy.getAnnotation(Repository.class) == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                + " is annotated with @OneToMany, but the referenced type '" + ((DeclaredType) mappedBy).asElement().getSimpleName() + "' is not annotated with @Repository.",
                        enclosedElement);
            }
        }
    }

    private static TypeMirror getMappedBy(OneToMany oneToMany) {
        try {
            oneToMany.mappedBy();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        throw new IllegalStateException("Should not be null.");
    }

    private void checkDeclared(TypeMirror fieldType, VariableElement enclosedElement, Element element, String x) {
        if (fieldType.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) fieldType;
            Element typeElement = declaredType.asElement();
            if (typeElement.getAnnotation(Repository.class) == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + enclosedElement.getSimpleName() + "' in " + element.getSimpleName()
                                + x + typeElement.getSimpleName() + "' is not annotated with @Repository.",
                        enclosedElement);
            }
        }
    }

    private void checkRawTypes(@NotNull VariableElement fieldElement) {
        TypeMirror type = fieldElement.asType();
        if (type.getKind() != TypeKind.DECLARED) return;

        DeclaredType declaredType = (DeclaredType) type;
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement typeElement)) return;

        // Only check types that declare type parameters (e.g., List<T>, Map<K, V>, etc.)
        if (typeElement.getTypeParameters().isEmpty()) return;

        // If the user didn't specify type arguments (it's raw), raise an error
        if (declaredType.getTypeArguments().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldElement.getSimpleName() + "' is using a raw type '" + typeElement.getSimpleName() + "'. " +
                            "You must specify type parameters (e.g., List<String>, Map<Key, Value>).",
                    fieldElement);
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
