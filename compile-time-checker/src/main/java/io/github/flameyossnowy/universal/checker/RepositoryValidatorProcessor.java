package io.github.flameyossnowy.universal.checker;

import io.github.flameyossnowy.universal.api.annotations.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({
    "io.github.flameyossnowy.universal.api.annotations.Repository",
    "io.github.flameyossnowy.universal.api.annotations.Cacheable",
    "io.github.flameyossnowy.universal.api.annotations.GlobalCacheable",
    "io.github.flameyossnowy.universal.api.annotations.ManyToOne",
    "io.github.flameyossnowy.universal.api.annotations.OneToMany",
    "io.github.flameyossnowy.universal.api.annotations.OneToOne",
    "io.github.flameyossnowy.universal.api.annotations.Constraint",
    "io.github.flameyossnowy.universal.api.annotations.Index",
    "io.github.flameyossnowy.universal.api.annotations.ExternalRepository"
})
public class RepositoryValidatorProcessor extends AbstractProcessor {

    private Messager messager;

    // Tracks repository names per processing round to avoid cross-round issues
    private final Set<String> repositoryNames = new HashSet<>();

    private static final List<String> FIELD_ANNOTATIONS = List.of(
        ManyToOne.class.getCanonicalName(),
        OneToMany.class.getCanonicalName(),
        OneToOne.class.getCanonicalName(),
        Unique.class.getCanonicalName(),
        NonNull.class.getCanonicalName(),
        Now.class.getCanonicalName(),
        Id.class.getCanonicalName(),
        AutoIncrement.class.getCanonicalName(),
        Named.class.getCanonicalName(),
        OnDelete.class.getCanonicalName(),
        OnUpdate.class.getCanonicalName(),
        Constraint.class.getCanonicalName(),
        DefaultValue.class.getCanonicalName(),
        DefaultValueProvider.class.getCanonicalName(),
        ExternalRepository.class.getCanonicalName()
    );

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Repository.class);
        if (elements.isEmpty()) return false;

        for (Element element : elements) {
            if (!(element instanceof TypeElement typeElement)) continue;

            ElementKind kind = typeElement.getKind();

            handleRepository(typeElement, kind);

            boolean hasNoArgConstructor = typeElement.getEnclosedElements().stream()
                .anyMatch(e -> e.getKind() == ElementKind.CONSTRUCTOR &&
                    ((ExecutableElement) e).getParameters().isEmpty());

            boolean hasIdField = typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .anyMatch(e -> hasAnnotation(e, Id.class.getCanonicalName()));

            boolean hasRelationshipField = typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .anyMatch(e -> hasAnnotation(e, OneToOne.class.getCanonicalName()) ||
                    hasAnnotation(e, OneToMany.class.getCanonicalName()));

            for (Element enclosedElement : typeElement.getEnclosedElements()) {
                if (enclosedElement.getKind() != ElementKind.FIELD) continue;
                handleField(typeElement, (VariableElement) enclosedElement);
            }

            if (hasRelationshipField && !hasIdField) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@Repository " + typeElement.getSimpleName() + " has relationship fields but no @Id field.",
                    typeElement);
            }

            if (!hasNoArgConstructor) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@Repository " + typeElement.getSimpleName() + " must have a no-arg constructor",
                    typeElement);
            }
        }

        return true;
    }

    private void handleRepository(TypeElement element, ElementKind kind) {
        if (!kind.isClass()) return;

        Repository repository = element.getAnnotation(Repository.class);
        if (repository == null) return;

        String repoName = repository.name();
        if (repoName == null || repoName.isBlank()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository " + element.getSimpleName() + " name cannot be empty",
                element);
            return;
        }

        if (repositoryNames.contains(repoName)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository " + element.getSimpleName() + " repository name must be unique",
                element);
            return;
        }

        repositoryNames.add(repoName);

        if (element.getModifiers().contains(Modifier.ABSTRACT)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository " + element.getSimpleName() + " cannot be abstract",
                element);
        }

        if (kind == ElementKind.ENUM || kind == ElementKind.INTERFACE) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository " + element.getSimpleName() + " cannot be enum or interface",
                element);
        }

        checkIndexAndConstraintReferences(element);
    }

    private void checkIndexAndConstraintReferences(TypeElement classElement) {
        Set<String> fieldNames = classElement.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .map(e -> e.getSimpleName().toString())
            .collect(Collectors.toSet());

        for (Element enclosed : classElement.getEnclosedElements()) {
            getAnnotations(enclosed, Index.class.getCanonicalName()).forEach(am ->
                validateColumns("Index", getStringArrayValue(am, "fields"), fieldNames, enclosed, classElement));
            getAnnotations(enclosed, Constraint.class.getCanonicalName()).forEach(am ->
                validateColumns("Constraint", getStringArrayValue(am, "fields"), fieldNames, enclosed, classElement));
        }
    }

    private void validateColumns(String kind, List<String> columns, Set<String> validFields, Element target, TypeElement classElement) {
        for (String column : columns) {
            if (!validFields.contains(column)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@" + kind + " on " + classElement.getSimpleName() + " refers to unknown field: '" + column + "'",
                    target);
            }
        }
    }

    private void handleField(Element parent, VariableElement field) {
        if (field.getModifiers().contains(Modifier.STATIC)) checkFieldAnnotations(parent, field, true);
        if (field.getModifiers().contains(Modifier.TRANSIENT)) checkFieldAnnotations(parent, field, true);
        if (field.getModifiers().contains(Modifier.FINAL) && !field.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository " + parent.getSimpleName() + " cannot have final field " + field.getSimpleName(),
                field);
        }
        checkRelationshipFieldAnnotations(parent, field);
        checkRawTypes(field);
    }

    private void checkFieldAnnotations(Element parent, VariableElement field, boolean error) {
        for (String annotation : FIELD_ANNOTATIONS) {
            if (hasAnnotation(field, annotation)) {
                messager.printMessage(error ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING,
                    "@Repository " + parent.getSimpleName() + " field " + field.getSimpleName() +
                        " cannot have annotation " + annotation,
                    field);
            }
        }
    }

    private void checkRelationshipFieldAnnotations(Element parent, VariableElement field) {
        boolean hasOneToOne = hasAnnotation(field, OneToOne.class.getCanonicalName());
        boolean hasOneToMany = hasAnnotation(field, OneToMany.class.getCanonicalName());
        boolean hasManyToOne = hasAnnotation(field, ManyToOne.class.getCanonicalName());
        boolean hasExternal = hasAnnotation(field, ExternalRepository.class.getCanonicalName());

        int count = 0;
        if (hasOneToOne) count++;
        if (hasOneToMany) count++;
        if (hasManyToOne) count++;
        if (count > 1) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Field " + field.getSimpleName() + " has multiple relationship annotations",
                field);
        }
    }

    private void checkRawTypes(VariableElement field) {
        TypeMirror type = field.asType();
        if (type.getKind() != TypeKind.DECLARED) return;
        DeclaredType declaredType = (DeclaredType) type;
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement typeElement)) return;
        if (!typeElement.getTypeParameters().isEmpty() && declaredType.getTypeArguments().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Field " + field.getSimpleName() + " is raw type " + typeElement.getSimpleName(),
                field);
        }
    }

    private boolean hasAnnotation(Element element, String annotationCanonicalName) {
        return element.getAnnotationMirrors().stream()
            .anyMatch(am -> ((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationCanonicalName));
    }

    private List<AnnotationMirror> getAnnotations(Element element, String annotationCanonicalName) {
        return element.getAnnotationMirrors().stream()
            .filter(am -> ((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationCanonicalName))
            .toList();
    }

    private List<String> getStringArrayValue(AnnotationMirror am, String key) {
        return am.getElementValues().entrySet().stream()
            .filter(e -> e.getKey().getSimpleName().contentEquals(key))
            .map(e -> {
                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> list = (List<? extends AnnotationValue>) e.getValue().getValue();
                return list.stream().map(av -> av.getValue().toString()).toList();
            }).flatMap(List::stream).toList();
    }
}
