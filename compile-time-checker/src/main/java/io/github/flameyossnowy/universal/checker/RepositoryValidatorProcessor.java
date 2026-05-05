package io.github.flameyossnowy.universal.checker;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Binary;
import io.github.flameyossnowy.universal.api.annotations.Cacheable;
import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.DefaultValue;
import io.github.flameyossnowy.universal.api.annotations.DefaultValueProvider;
import io.github.flameyossnowy.universal.api.annotations.EnumAsOrdinal;
import io.github.flameyossnowy.universal.api.annotations.ExternalRepository;
import io.github.flameyossnowy.universal.api.annotations.FetchPageSize;
import io.github.flameyossnowy.universal.api.annotations.GlobalCacheable;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Index;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.JsonIndex;
import io.github.flameyossnowy.universal.api.annotations.JsonVersioned;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.Named;
import io.github.flameyossnowy.universal.api.annotations.NonNull;
import io.github.flameyossnowy.universal.api.annotations.Now;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.RepositoryAuditLogger;
import io.github.flameyossnowy.universal.api.annotations.RepositoryEventLifecycleListener;
import io.github.flameyossnowy.universal.api.annotations.RepositoryExceptionHandler;
import io.github.flameyossnowy.universal.api.annotations.Resolves;
import io.github.flameyossnowy.universal.api.annotations.Unique;
import io.github.flameyossnowy.universal.api.annotations.Validate;
import io.github.flameyossnowy.universal.api.annotations.Validations;
import io.github.flameyossnowy.universal.api.meta.ValidationModel;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.meta.JsonIndexModel;
import io.github.flameyossnowy.universal.api.meta.JsonStorageKind;
import io.github.flameyossnowy.universal.api.annotations.ResolveWith;
import io.github.flameyossnowy.universal.checker.generator.UnifiedFactoryGenerator;
import io.github.flameyossnowy.universal.checker.processor.AnnotationUtils;
import io.github.flameyossnowy.universal.checker.processor.TypeMirrorUtils;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static io.github.flameyossnowy.universal.api.meta.RelationshipKind.*;

@SuppressWarnings("ObjectAllocationInLoop")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes({
    "io.github.flameyossnowy.universal.api.annotations.Repository",
    "io.github.flameyossnowy.universal.api.annotations.Cacheable",
    "io.github.flameyossnowy.universal.api.annotations.GlobalCacheable",
    "io.github.flameyossnowy.universal.api.annotations.ManyToOne",
    "io.github.flameyossnowy.universal.api.annotations.OneToMany",
    "io.github.flameyossnowy.universal.api.annotations.OneToOne",
    "io.github.flameyossnowy.universal.api.annotations.Constraint",
    "io.github.flameyossnowy.universal.api.annotations.AutoIncrement",
    "io.github.flameyossnowy.universal.api.annotations.Index",
    "io.github.flameyossnowy.universal.api.annotations.ExternalRepository",
    "io.github.flameyossnowy.universal.api.annotations.Binary",
    "io.github.flameyossnowy.universal.api.annotations.Unique",
    "io.github.flameyossnowy.universal.api.annotations.FetchPageSize",
    "io.github.flameyossnowy.universal.api.annotations.EnumAsOrdinal",
    "io.github.flameyossnowy.universal.api.annotations.RepositoryAuditLogger",
    "io.github.flameyossnowy.universal.api.annotations.RepositoryEntityLifecycleHandler",
    "io.github.flameyossnowy.universal.api.annotations.RepositoryExceptionHandler",
    "io.github.flameyossnowy.universal.api.annotations.FileRepository",
    "io.github.flameyossnowy.universal.api.annotations.RemoteEndpoint",
    "io.github.flameyossnowy.universal.api.annotations.OnUpdate",
    "io.github.flameyossnowy.universal.api.annotations.OnDelete",
    "io.github.flameyossnowy.universal.api.annotations.Now",
    "io.github.flameyossnowy.universal.api.annotations.NonNull",
    "io.github.flameyossnowy.universal.api.annotations.Named",
    "io.github.flameyossnowy.universal.api.annotations.DefaultValue",
    "io.github.flameyossnowy.universal.api.annotations.DefaultValueProvider",
    "io.github.flameyossnowy.universal.api.annotations.Resolves",
    "io.github.flameyossnowy.universal.api.annotations.Validate",
    "io.github.flameyossnowy.universal.api.annotations.Validations",
})
public class RepositoryValidatorProcessor extends AbstractProcessor {
    private Types types;
    private Messager messager;
    private Elements elements;
    private Filer filer;

    private static final String RESOURCE =
        "META-INF/services/io.github.flameyossnowy.universal.api.GeneratedRepositoryFactory";

    private static final String ANN_KEY_VALUE    = "value";
    private static final String ANN_KEY_PRIORITY = "priority";
    private static final String ANN_KEY_FIELDS   = "fields";
    private static final String ANN_KEY_NAME     = "name";
    private static final String ANN_KEY_TYPE     = "type";

    private final List<String> qualifiedNames = new ArrayList<>(16);

    private final Set<String> repositoryNames = new HashSet<>(16);

    private TypeMirror map;
    private TypeMirror list;
    private TypeMirror set;
    private TypeMirror queue;
    private TypeMirror deque;

    private TypeMirror localDateTime;
    private TypeMirror localDate;
    private TypeMirror instant;
    private TypeMirror zonedDateTime;
    private TypeMirror offsetDateTime;
    private TypeMirror utilDate;
    private TypeMirror sqlTimestamp;

    private static final List<String> FIELD_ANNOTATIONS = List.of(
        ManyToOne.class.getCanonicalName(),
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
        ExternalRepository.class.getCanonicalName(),
        Binary.class.getCanonicalName(),
        ResolveWith.class.getCanonicalName()
    );

    private PrimitiveType primitiveBoolean;
    private TypeMirror boxedBoolean;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();

        Elements elements = processingEnv.getElementUtils();
        this.elements = elements;

        this.map   = TypeMirrorUtils.typeOf(Map.class,   elements);
        this.list  = TypeMirrorUtils.typeOf(List.class,  elements);
        this.set   = TypeMirrorUtils.typeOf(Set.class,   elements);
        this.queue = TypeMirrorUtils.typeOf(Queue.class, elements);
        this.deque = TypeMirrorUtils.typeOf(Deque.class, elements);

        this.primitiveBoolean = types.getPrimitiveType(TypeKind.BOOLEAN);
        this.boxedBoolean = elements.getTypeElement("java.lang.Boolean").asType();

        this.localDateTime   = resolveType("java.time.LocalDateTime");
        this.localDate       = resolveType("java.time.LocalDate");
        this.instant         = resolveType("java.time.Instant");
        this.zonedDateTime   = resolveType("java.time.ZonedDateTime");
        this.offsetDateTime  = resolveType("java.time.OffsetDateTime");
        this.utilDate        = resolveType("java.util.Date");
        this.sqlTimestamp    = resolveType("java.sql.Timestamp");
    }

    private TypeMirror resolveType(String fqcn) {
        TypeElement te = elements.getTypeElement(fqcn);
        return te == null ? null : te.asType();
    }

    /**
     * Returns true if {@code type} is assignable to any of the known temporal types.
     * Uses the Types API — no string matching.
     */
    private boolean isTemporalType(TypeMirror type) {
        TypeMirror erased = types.erasure(type);
        for (TypeMirror temporal : new TypeMirror[]{
            localDateTime, localDate, instant,
            zonedDateTime, offsetDateTime, utilDate, sqlTimestamp
        }) {
            if (temporal != null && types.isAssignable(erased, types.erasure(temporal))) {
                return true;
            }
        }
        return false;
    }

    private final Map<String, ResolverInfo> globalTypeResolvers = new HashMap<>(16);
    private final Set<String> scannedResolvers = new HashSet<>(16);

    private record ResolverInfo(String resolverClassName, int priority) {
    }

    private void scanResolverAnnotations(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Resolves.class)) {
            if (!(element instanceof TypeElement resolverClass)) {
                continue;
            }

            String resolverFqcn = resolverClass.getQualifiedName().toString();
            if (!scannedResolvers.add(resolverFqcn)) {
                continue;
            }

            if (!implementsTypeResolver(resolverClass)) {
                messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@Resolves can only be used on classes implementing TypeResolver",
                    element
                );
                continue;
            }

            AnnotationMirror resolvesMirror = AnnotationUtils.getAnnotationMirror(
                element,
                "io.github.flameyossnowy.universal.api.resolver.Resolves"
            );

            if (resolvesMirror == null) continue;

            List<TypeMirror> handledTypes = new ArrayList<>(16);
            int priority = 0;

            for (var entry : resolvesMirror.getElementValues().entrySet()) {
                String key = entry.getKey().getSimpleName().toString();

                if (ANN_KEY_VALUE.equals(key)) {
                    @SuppressWarnings("unchecked")
                    List<AnnotationValue> values = (List<AnnotationValue>) entry.getValue().getValue();
                    for (AnnotationValue val : values) {
                        TypeMirror type = (TypeMirror) val.getValue();
                        handledTypes.add(type);
                    }
                } else if (ANN_KEY_PRIORITY.equals(key)) {
                    priority = (int) entry.getValue().getValue();
                }
            }

            for (TypeMirror handledType : handledTypes) {
                String typeQualifiedName = TypeMirrorUtils.qualifiedName(handledType);

                ResolverInfo existing = globalTypeResolvers.get(typeQualifiedName);
                if (existing != null) {
                    if (priority > existing.priority) {
                        globalTypeResolvers.put(
                            typeQualifiedName,
                            new ResolverInfo(resolverFqcn, priority)
                        );
                    } else if (priority == existing.priority) {
                        messager.printMessage(
                            Diagnostic.Kind.WARNING,
                            "Multiple @Resolves with same priority (" + priority + ") for type: " + typeQualifiedName,
                            element
                        );
                    }
                } else {
                    globalTypeResolvers.put(
                        typeQualifiedName,
                        new ResolverInfo(resolverFqcn, priority)
                    );
                }
            }
        }
    }

    private boolean implementsTypeResolver(TypeElement element) {
        TypeMirror typeResolverType = TypeMirrorUtils.typeOf(
            io.github.flameyossnowy.universal.api.resolver.TypeResolver.class,
            elements
        );

        if (typeResolverType == null) return false;

        for (TypeMirror iface : element.getInterfaces()) {
            TypeMirror erasedIface = types.erasure(iface);
            TypeMirror erasedTypeResolver = types.erasure(typeResolverType);

            if (types.isSameType(erasedIface, erasedTypeResolver)) {
                return true;
            }
        }

        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE) {
            TypeElement superElement = (TypeElement) types.asElement(superclass);
            if (superElement != null) {
                return implementsTypeResolver(superElement);
            }
        }

        return false;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            scanResolverAnnotations(roundEnv);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Repository.class)) {
            if (!(element instanceof TypeElement type)) continue;

            List<VariableElement> fields  = new ArrayList<>(16);
            List<ExecutableElement> methods = new ArrayList<>(16);
            boolean hasNoArgCtor = false;

            for (Element e : type.getEnclosedElements()) {
                switch (e.getKind()) {
                    case CONSTRUCTOR -> {
                        if (((ExecutableElement) e).getParameters().isEmpty()) {
                            hasNoArgCtor = true;
                        }
                    }
                    case FIELD  -> fields.add((VariableElement) e);
                    case METHOD -> methods.add((ExecutableElement) e);
                    default -> { /* ignored */ }
                }
            }

            boolean valid = handleRepository(type, type.getKind(), fields, methods);
            if (!valid) continue;

            boolean hasId = false;
            boolean hasRelationship = false;
            for (VariableElement f : fields) {
                if (AnnotationUtils.hasAnnotation(f, Id.class.getCanonicalName())) hasId = true;
                if (AnnotationUtils.hasAnnotation(f, OneToOne.class.getCanonicalName())
                    || AnnotationUtils.hasAnnotation(f, OneToMany.class.getCanonicalName())
                    || AnnotationUtils.hasAnnotation(f, ManyToOne.class.getCanonicalName())) {
                    hasRelationship = true;
                }
                handleField(f);
            }

            if (hasRelationship && !hasId) {
                error(
                    "@Repository " + type.getSimpleName() +
                        " has relationship fields but no @Id field.",
                    type);
            }

            if (!hasNoArgCtor && type.getKind() != ElementKind.RECORD) {
                error(
                    "@Repository " + type.getSimpleName() +
                        " must have a no-arg constructor",
                    type);
            }
        }
        if (roundEnv.processingOver()) {
            writeResource();
        }
        return true;
    }

    /**
     * Validates the repository-level constraints and triggers codegen.
     *
     * @return false if any validation error was emitted (caller should skip codegen)
     */
    private boolean handleRepository(TypeElement element, ElementKind kind,
                                     List<VariableElement> fields,
                                     List<ExecutableElement> methods) {
        if (!kind.isClass()) return false;

        Repository repo = element.getAnnotation(Repository.class);
        if (repo == null) return false;

        boolean valid = true;

        if (repo.name().isBlank()) {
            error(
                "@Repository name cannot be empty", element);
            valid = false;
        }

        if (!repositoryNames.add(repo.name())) {
            error(
                "@Repository name must be unique", element);
            valid = false;
        }

        if (element.getModifiers().contains(Modifier.ABSTRACT)) {
            error(
                "@Repository cannot be abstract", element);
            valid = false;
        }

        if (kind == ElementKind.INTERFACE || kind == ElementKind.ENUM) {
            error(
                "@Repository cannot be enum or interface", element);
            valid = false;
        }

        if (!valid) return false;

        checkIndexAndConstraintReferences(element, fields);
        checkAccessors(element, fields, methods);

        RepositoryModel model = buildRepositoryModel(element, fields);

        Set<String> fieldNames = new HashSet<>(16);
        for (FieldModel field : model.fields()) {
            fieldNames.add(field.name());
        }

        validateIndexes(model.indexes(), fieldNames, element);
        validateConstraints(model.constraints(), fieldNames, element);
        UnifiedFactoryGenerator gen = new UnifiedFactoryGenerator(processingEnv, processingEnv.getFiler());
        gen.generate(model);
        qualifiedNames.addAll(gen.getQualifiedNames());
        return true;
    }

    public void writeResource() {
        try {
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", RESOURCE);

            try (Writer w = file.openWriter()) {
                for (String fqcn : qualifiedNames) {
                    w.write(fqcn);
                    w.write('\n');
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't write resource.", e);
        }
    }

    private void checkAccessors(TypeElement entity,
                                List<VariableElement> fields,
                                List<ExecutableElement> methodList) {
        if (entity.getKind() == ElementKind.RECORD) return;

        Map<String, ExecutableElement> methods = new HashMap<>(methodList.size() * 2);
        for (ExecutableElement m : methodList) {
            methods.put(m.getSimpleName().toString(), m);
        }

        for (VariableElement field : fields) {
            String name = field.getSimpleName().toString();
            String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);

            ExecutableElement getter = methods.get("get" + cap);
            if (getter == null || !getter.getParameters().isEmpty()) {
                error(
                    "Missing getter get" + cap + "() for field " + name, field);
            }

            if (!field.getModifiers().contains(Modifier.FINAL)) {
                TypeMirror typeMirror = field.asType();
                boolean isPrimitive = types.isSameType(typeMirror, primitiveBoolean);
                boolean isBoxed     = types.isSameType(typeMirror, boxedBoolean);
                detectUnapplicableSetter(methods, cap, isPrimitive, isBoxed, name, field);
            }
        }
    }

    private void detectUnapplicableSetter(Map<String, ExecutableElement> methods, String cap, boolean isPrimitive, boolean isBoxed, String name, VariableElement field) {
        ExecutableElement setter = methods.get("set" + cap);
        if (isPrimitive || isBoxed) {
            ExecutableElement commonSetter = methods.get("is" + cap);
            if (bothSettersUnapplicable(setter, commonSetter)) {
                error(
                    "Missing setter set" + cap + "(..) for field " + name, field);
            }
        } else {
            if (setter == null || setter.getParameters().size() != 1) {
                error(
                    "Missing setter set" + cap + "(..) for field " + name, field);
            }
        }
    }

    private static boolean bothSettersUnapplicable(ExecutableElement setter, ExecutableElement commonSetter) {
        return (setter == null || setter.getParameters().size() != 1)
            && (commonSetter == null || commonSetter.getParameters().size() != 1);
    }

    private void checkIndexAndConstraintReferences(TypeElement type,
                                                   List<VariableElement> fields) {
        Set<String> fieldNames = new HashSet<>(fields.size() * 2);
        for (VariableElement f : fields) {
            fieldNames.add(f.getSimpleName().toString());
        }

        for (VariableElement f : fields) {
            for (AnnotationMirror am : AnnotationUtils.getAnnotations(f, Index.class.getCanonicalName())) {
                validateColumns("Index", AnnotationUtils.getStringArrayValue(am), fieldNames, f);
            }
            for (AnnotationMirror am : AnnotationUtils.getAnnotations(f, Constraint.class.getCanonicalName())) {
                validateColumns("Constraint", AnnotationUtils.getStringArrayValue(am), fieldNames, f);
            }
        }
    }

    private void validateColumns(
        String kind,
        List<String> cols,
        Set<String> valid,
        Element target
    ) {
        for (String c : cols) {
            if (!valid.contains(c)) {
                error(
                    "@" + kind + " refers to unknown field '" + c + "'",
                    target);
            }
        }
    }

    private void handleField(VariableElement field) {
        if (field.getModifiers().contains(Modifier.STATIC)
            || field.getModifiers().contains(Modifier.TRANSIENT)) {
            checkFieldAnnotations(field);
        }

        if (field.getModifiers().contains(Modifier.FINAL)
            && !field.getModifiers().contains(Modifier.STATIC)) {
            error(
                "Final fields are not allowed", field);
        }

        checkRelationshipFieldAnnotations(field);

        boolean hasResolver =
            field.getAnnotation(ResolveWith.class) != null ||
                globalTypeResolvers.containsKey(TypeMirrorUtils.qualifiedName(field.asType()));

        if (hasResolver) {
            // Resolvers handle serialization, but structural constraints still apply.
            // Raw type check is intentionally skipped as the resolver owns the type.
            return;
        }

        checkRawTypes(field);
        checkCollectionOfMaps(field);
    }

    private void checkCollectionOfMaps(VariableElement field) {
        TypeMirror fieldType = field.asType();

        if (TypeMirrorUtils.isCollectionOfMaps(types, elements, fieldType)) {
            if (field.getAnnotation(JsonField.class) == null) {
                error("Collection of Maps (e.g., List<Map<...>>) is not supported. " +
                      "Add @JsonField to store as JSON, or use a different data structure.", field);
            }
        }
    }

    private void checkFieldAnnotations(VariableElement field) {
        for (String ann : FIELD_ANNOTATIONS) {
            if (AnnotationUtils.hasAnnotation(field, ann)) {
                error("Field " + field.getSimpleName() +
                        " cannot have @" + ann, field);
            }
        }
    }

    private void checkRelationshipFieldAnnotations(VariableElement field) {
        int count = 0;
        if (AnnotationUtils.hasAnnotation(field, OneToOne.class.getCanonicalName())) count++;
        if (AnnotationUtils.hasAnnotation(field, OneToMany.class.getCanonicalName())) count++;
        if (AnnotationUtils.hasAnnotation(field, ManyToOne.class.getCanonicalName())) count++;

        if (count > 1) {
            error("Multiple relationship annotations", field);
        }
    }

    private void checkRawTypes(VariableElement field) {
        if (!(field.asType() instanceof DeclaredType dt)) return;

        TypeElement te = (TypeElement) dt.asElement();

        if (te.getTypeParameters().isEmpty()) {
            return;
        }

        if (dt.getTypeArguments().isEmpty()) {
            error(
                "Raw generic type not allowed - please specify type parameters", field);
        }
    }

    private RepositoryModel buildRepositoryModel(TypeElement entity,
                                                 List<VariableElement> enclosedFields) {
        String packageName =
            elements.getPackageOf(entity).getQualifiedName().toString();

        Repository repo = entity.getAnnotation(Repository.class);
        FetchPageSize fetchPageSize = entity.getAnnotation(FetchPageSize.class);

        Cacheable cacheable = entity.getAnnotation(Cacheable.class);
        GlobalCacheable globalCacheable = entity.getAnnotation(GlobalCacheable.class);

        CacheConfig cacheConfig;
        if (cacheable != null) {
            cacheConfig = new CacheConfig(
                cacheable.maxCacheSize(),
                cacheable.algorithm()
            );
        } else {
            cacheConfig = CacheConfig.none();
        }

        List<FieldModel> fields = new ArrayList<>(16);
        List<FieldModel> primaryKeys = new ArrayList<>(16);

        List<io.github.flameyossnowy.universal.checker.IndexModel> indexes = extractIndexes(entity);
        Set<io.github.flameyossnowy.universal.checker.IndexModel> indexModelSet = new LinkedHashSet<>(indexes);

        // Extract field-level indexes and add them to the set
        List<io.github.flameyossnowy.universal.checker.IndexModel> fieldIndexes = extractFieldIndexes(entity, enclosedFields);
        indexModelSet.addAll(fieldIndexes);

        List<ConstraintModel> constraints = extractConstraints(entity);
        List<RelationshipModel> relationships = new ArrayList<>(8);

        // Build method map once for getter/setter lookup
        Map<String, ExecutableElement> methodMap = new HashMap<>(32);
        for (Element e : entity.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                methodMap.put(e.getSimpleName().toString(), (ExecutableElement) e);
            }
        }

        TypeMirror idType = null;

        for (VariableElement field : enclosedFields) {
            boolean isId = AnnotationUtils.hasAnnotation(field, Id.class.getCanonicalName());
            if (isId) {
                idType = field.asType();
            }

            boolean auto = AnnotationUtils.hasAnnotation(field, AutoIncrement.class.getCanonicalName());

            String columnName = field.getSimpleName().toString();
            Named named = field.getAnnotation(Named.class);
            if (named != null && !named.value().isBlank()) {
                columnName = named.value();
            }

            TypeMirror type = field.asType();

            String fieldName = field.getSimpleName().toString();
            String resolveWithClass = null;
            ResolveWith resolveWith = field.getAnnotation(ResolveWith.class);

            if (resolveWith != null) {
                AnnotationMirror resolveWithMirror = AnnotationUtils.getAnnotationMirror(
                    field,
                    ResolveWith.class.getCanonicalName()
                );
                if (resolveWithMirror != null) {
                    TypeMirror resolverType = AnnotationUtils.getClassValue(resolveWithMirror, ANN_KEY_VALUE);
                    if (resolverType != null) {
                        resolveWithClass = TypeMirrorUtils.qualifiedName(resolverType);
                    }
                }
            }

            if (resolveWithClass == null) {
                String fieldTypeQualifiedName = TypeMirrorUtils.qualifiedName(type);
                ResolverInfo globalResolver = globalTypeResolvers.get(fieldTypeQualifiedName);

                if (globalResolver != null) {
                    resolveWithClass = globalResolver.resolverClassName;
                }
            }

            boolean hasCustomResolver = resolveWithClass != null;

            String capitalized =
                Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            boolean nullable =
                !isId && !AnnotationUtils.hasAnnotation(field, NonNull.class.getCanonicalName());

            boolean insertable = !auto;
            boolean updatable = !isId;

            boolean hasNow = AnnotationUtils.hasAnnotation(field, Now.class.getCanonicalName());
            boolean hasBinary = AnnotationUtils.hasAnnotation(field, Binary.class.getCanonicalName());
            boolean hasUnique = AnnotationUtils.hasAnnotation(field, Unique.class.getCanonicalName());
            boolean enumAsOrdinal = AnnotationUtils.hasAnnotation(field, EnumAsOrdinal.class.getCanonicalName());

            String defaultValue = null;
            DefaultValue defaultValueAnn = field.getAnnotation(DefaultValue.class);

            if (defaultValueAnn != null) {
                defaultValue = defaultValueAnn.value();
            }

            String defaultValueProviderClass = null;
            DefaultValueProvider providerAnn = field.getAnnotation(DefaultValueProvider.class);
            if (providerAnn != null) {
                AnnotationMirror providerMirror = AnnotationUtils.getAnnotationMirror(
                    field,
                    DefaultValueProvider.class.getCanonicalName()
                );
                if (providerMirror != null) {
                    TypeMirror providerType = AnnotationUtils.getClassValue(providerMirror, ANN_KEY_VALUE);
                    if (providerType != null) {
                        defaultValueProviderClass = TypeMirrorUtils.qualifiedName(providerType);

                        if ("java.lang.Void".equals(defaultValueProviderClass)) {
                            error("@DefaultValueProvider cannot be void.class", field);
                            defaultValueProviderClass = null;
                        }
                    }
                }
            }

            String externalRepo = null;
            ExternalRepository extRepo = field.getAnnotation(ExternalRepository.class);
            if (extRepo != null && !extRepo.adapter().isBlank()) {
                externalRepo = extRepo.adapter();
            }

            if (defaultValue != null && defaultValueProviderClass != null) {
                error("Field cannot have both @DefaultValue and @DefaultValueProvider", field);
            }

            if (!hasCustomResolver) {
                if (hasNow) {
                    if (!isTemporalType(type)) {
                        error("@Now can only be used on temporal types (LocalDateTime, Date, etc.)", field);
                    }
                }

                if (enumAsOrdinal) {
                    TypeElement typeElement = null;
                    if (type instanceof DeclaredType dt) {
                        typeElement = (TypeElement) dt.asElement();
                    }
                    if (typeElement == null || typeElement.getKind() != ElementKind.ENUM) {
                        error("@EnumAsOrdinal can only be used on enum fields", field);
                    }
                }
            }

            Condition condition = field.getAnnotation(Condition.class);

            // Extract @Validate annotations and create ValidationModel
            ValidationModel validation = extractValidationModel(field);

            // Warn if both @Condition (deprecated) and @Validate are used
            if (condition != null && validation != null) {
                warn("Field '" + fieldName + "' has both @Condition (deprecated) and @Validate. " +
                     "@Validate takes precedence. Consider migrating to @Validate.", field);
            }

            OnDelete onDelete = field.getAnnotation(OnDelete.class);
            OnUpdate onUpdate = field.getAnnotation(OnUpdate.class);

            TypeMirror elementType = null;
            TypeMirror mapKeyType = null;
            TypeMirror mapValueType = null;

            CollectionKind collectionKind = CollectionKind.OTHER;
            RelationshipModel rel = null;

            if (!hasCustomResolver) {
                elementType = resolveElementTypeMirror(type);
                mapKeyType = resolveMapKeyTypeMirror(type);
                mapValueType = resolveMapValueTypeMirror(type);

                if (TypeMirrorUtils.isList(types, elements, type)) {
                    collectionKind = CollectionKind.LIST;
                } else if (TypeMirrorUtils.isSet(types, elements, type)) {
                    collectionKind = CollectionKind.SET;
                } else if (TypeMirrorUtils.isMap(types, elements, type)) {
                    if (TypeMirrorUtils.isCollectionValueMap(types, elements, type)) {
                        collectionKind = CollectionKind.MULTIMAP;
                    } else {
                        collectionKind = CollectionKind.MAP;
                    }
                }

                rel = extractRelationship(field);
                if (rel != null) {
                    relationships.add(rel);
                }
            }

            boolean indexed = checkIfIndexed(indexModelSet, fieldName);

            AnnotationMirror jsonField = AnnotationUtils.getAnnotationMirror(field, JsonField.class.getCanonicalName());
            boolean isJson = jsonField != null;
            JsonStorageKind jsonStorageKind = JsonStorageKind.COLUMN;
            String jsonColumnDefinition = null;
            String jsonCodecClass = "io.github.flameyossnowy.universal.api.json.DefaultJsonCodec";
            boolean jsonQueryable = false;
            boolean jsonPartialUpdate = false;
            boolean jsonVersioned = false;

            String getterGet = "get" + capitalized;
            String getterIs = "is" + capitalized;
            String getter = getGetterName(methodMap, getterGet, getterIs, fieldName);

            String setterSet = "set" + capitalized;
            String setterWithoutIs = "set" + (capitalized.startsWith("Is") ? capitalized.substring(2) : capitalized);
            String setter = getSetterName(methodMap, setterSet, setterWithoutIs, fieldName);

            if (getter == null) {
                error("Missing getter method for field " + field, field);
                continue;
            }

            if (isJson) {
                jsonVersioned = field.getAnnotation(JsonVersioned.class) != null;

                String storageName = AnnotationUtils.getEnumValueName(jsonField, "storage");
                if ("TABLE".equals(storageName)) {
                    jsonStorageKind = JsonStorageKind.TABLE;
                }

                String columnDef = AnnotationUtils.getStringValue(jsonField, "columnDefinition");
                if (columnDef != null && !columnDef.isBlank()) {
                    jsonColumnDefinition = columnDef;
                }

                TypeMirror codecMirror = AnnotationUtils.getClassValue(jsonField, "codec");
                jsonCodecClass = codecMirror == null
                    ? "io.github.flameyossnowy.universal.api.json.DefaultJsonCodec"
                    : TypeMirrorUtils.qualifiedName(codecMirror);

                jsonQueryable = AnnotationUtils.getBooleanValue(jsonField, "queryable");
                jsonPartialUpdate = AnnotationUtils.getBooleanValue(jsonField, "supportsPartialUpdate");
            }

            List<AnnotationMirror> jsonIndexMirrors = AnnotationUtils.getAnnotations(field, JsonIndex.class.getCanonicalName());
            List<JsonIndexModel> jsonIndexes;
            if (jsonIndexMirrors.isEmpty()) {
                jsonIndexes = List.of();
            } else {
                jsonIndexes = new ArrayList<>(jsonIndexMirrors.size());
                for (AnnotationMirror jsonIndex : jsonIndexMirrors) {
                    String path = AnnotationUtils.getStringValue(jsonIndex, "path");
                    boolean unique = AnnotationUtils.getBooleanValue(jsonIndex, "unique");
                    jsonIndexes.add(new JsonIndexModel(path, unique));
                }
            }

            FieldModel model = new FieldModel(
                fieldName,
                columnName,
                type,
                TypeMirrorUtils.qualifiedName(type),
                isId,
                auto,
                nullable,
                rel != null,
                rel == null ? null : rel.relationshipKind(),
                rel == null ? Consistency.NONE : rel.consistency(),
                getter,
                setter,
                rel != null && rel.lazy(),
                insertable,
                updatable,
                hasNow,
                hasBinary,
                hasUnique,
                defaultValue,
                defaultValueProviderClass,
                enumAsOrdinal,
                externalRepo,
                condition,
                validation,
                onDelete,
                onUpdate,
                resolveWithClass,
                elementType,
                mapKeyType,
                mapValueType,
                indexed,
                collectionKind,
                isJson,
                jsonStorageKind,
                jsonColumnDefinition,
                jsonCodecClass,
                jsonQueryable,
                jsonPartialUpdate,
                jsonVersioned,
                jsonIndexes
            );

            fields.add(model);
            if (isId) primaryKeys.add(model);
        }

        AnnotationMirror auditLogger =
            AnnotationUtils.getAnnotationMirror(entity, RepositoryAuditLogger.class.getCanonicalName());

        AnnotationMirror exceptionHandler =
            AnnotationUtils.getAnnotationMirror(entity, RepositoryExceptionHandler.class.getCanonicalName());

        AnnotationMirror lifecycleListener =
            AnnotationUtils.getAnnotationMirror(entity, RepositoryEventLifecycleListener.class.getCanonicalName());

        TypeMirror auditLoggerType = AnnotationUtils.getClassValue(auditLogger, ANN_KEY_VALUE);
        TypeMirror exceptionHandlerType = AnnotationUtils.getClassValue(exceptionHandler, ANN_KEY_VALUE);
        TypeMirror lifecycleListenerType = AnnotationUtils.getClassValue(lifecycleListener, ANN_KEY_VALUE);

        // Extract credentialsProvider from @NetworkRepository if present
        AnnotationMirror networkRepo =
            AnnotationUtils.getAnnotationMirror(entity, "io.github.flameyossnowy.universal.api.annotations.NetworkRepository");
        TypeMirror credentialsProviderType = null;
        if (networkRepo != null) {
            credentialsProviderType = AnnotationUtils.getClassValue(networkRepo, "credentialsProvider");
            // Check if it's void.class (default value)
            if (credentialsProviderType != null) {
                String qn = TypeMirrorUtils.qualifiedName(credentialsProviderType);
                if ("java.lang.Void".equals(qn)) {
                    credentialsProviderType = null;
                }
            }
        }

        //noinspection unchecked
        return new RepositoryModel(
            packageName,
            entity.getSimpleName().toString(),
            entity.getQualifiedName().toString(),
            Objects.requireNonNull(repo).name(),
            entity.getKind() == ElementKind.RECORD,
            entity.asType(),
            idType,
            fields,
            primaryKeys,
            indexes,
            constraints,
            relationships,
            fetchPageSize == null ? -1 : fetchPageSize.value(),
            cacheable != null,
            cacheConfig,
            globalCacheable != null,
            globalCacheable != null ? (Class<? extends SessionCache<?, ?>>) globalCacheable.sessionCache() : null,
            auditLoggerType,
            exceptionHandlerType,
            lifecycleListenerType,
            credentialsProviderType
        );
    }

    private static String getGetterName(Map<String, ExecutableElement> methods,
                                        String getterGet, String getterIs, String fieldName) {
        if (methods.containsKey(getterGet)) return getterGet;
        if (methods.containsKey(getterIs)) return getterIs;
        if (methods.containsKey(fieldName)) return fieldName;
        return null;
    }

    private static String getSetterName(Map<String, ExecutableElement> methods,
                                        String setterSet, String setterWithoutIs, String fieldName) {
        if (methods.containsKey(setterSet)) return setterSet;
        if (methods.containsKey(setterWithoutIs)) return setterWithoutIs;
        if (methods.containsKey(fieldName)) return fieldName;
        return null;
    }

    private static boolean checkIfIndexed(Collection<io.github.flameyossnowy.universal.checker.IndexModel> indexes, String fieldName) {
        for (io.github.flameyossnowy.universal.checker.IndexModel index : indexes) {
            if (index.fields().contains(fieldName)) {
                return true;
            }
        }

        return false;
    }

    private static TypeMirror resolveElementTypeMirror(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (!args.isEmpty()) {
                String rawName = dt.asElement().toString();
                if (rawName.equals("java.util.List") || rawName.equals("java.util.Set")) {
                    return args.getFirst();
                }
                if (rawName.equals("java.util.Map") && args.size() == 2) {
                    return args.get(1);
                }
            }
        }
        return null;
    }

    private static TypeMirror resolveMapKeyTypeMirror(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (!args.isEmpty() && dt.asElement().toString().equals("java.util.Map")) {
                return args.getFirst();
            }
        }
        return null;
    }

    private static TypeMirror resolveMapValueTypeMirror(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (!args.isEmpty() && dt.asElement().toString().equals("java.util.Map") && args.size() == 2) {
                return args.get(1);
            }
        }
        return null;
    }

    private RelationshipModel extractRelationship(VariableElement field) {
        AnnotationMirror otm = AnnotationUtils.getAnnotationMirror(field, OneToMany.class.getCanonicalName());
        AnnotationMirror mto = AnnotationUtils.getAnnotationMirror(field, ManyToOne.class.getCanonicalName());
        AnnotationMirror oto = AnnotationUtils.getAnnotationMirror(field, OneToOne.class.getCanonicalName());
        AnnotationMirror named = AnnotationUtils.getAnnotationMirror(field, Named.class.getCanonicalName());

        int count = (otm != null ? 1 : 0)
            + (mto != null ? 1 : 0)
            + (oto != null ? 1 : 0);

        if (count == 0) return null;

        if (count > 1) {
            error("Field may only have one relationship annotation", field);
            return null;
        }

        String columnName = AnnotationUtils.getStringValue(named, ANN_KEY_VALUE);
        if (columnName == null) {
            columnName = field.getSimpleName().toString();
        }
        if (otm != null) return extractOneToMany(field, otm, columnName);
        if (mto != null) return extractManyToOne(field, mto, columnName);
        return extractOneToOne(field, oto, columnName);
    }

    /**
     * Extracts validation information from @Validate annotations on a field.
     * Handles both single @Validate and repeatable @Validations.
     *
     * @param field the field element to extract validation from
     * @return ValidationModel containing all validation rules, or null if no validation
     */
    private ValidationModel extractValidationModel(VariableElement field) {
        // Get @Validations container (for multiple @Validate annotations)
        AnnotationMirror validationsContainer = AnnotationUtils.getAnnotationMirror(
            field, Validations.class.getCanonicalName());

        // Get single @Validate annotation
        AnnotationMirror singleValidate = AnnotationUtils.getAnnotationMirror(
            field, Validate.class.getCanonicalName());

        List<AnnotationMirror> validateAnnotations = new ArrayList<>();

        if (validationsContainer != null) {
            // Extract all @Validate from the container
            List<AnnotationMirror> nested = AnnotationUtils.getAnnotationValue(validationsContainer, "value", elements);
            if (nested != null) {
                validateAnnotations.addAll(nested);
            }
        }

        if (singleValidate != null) {
            validateAnnotations.add(singleValidate);
        }

        if (validateAnnotations.isEmpty()) {
            return null;
        }

        // Collect all rules and parameters from all @Validate annotations
        List<Validate.Rule> allRules = new ArrayList<>();
        Map<String, String> allParams = new HashMap<>();
        String customValidatorClass = null;
        String message = null;

        for (AnnotationMirror validate : validateAnnotations) {
            // Extract rules
            @SuppressWarnings("unchecked")
            List<AnnotationMirror> rules = AnnotationUtils.getAnnotationValue(validate, "value", elements);
            if (rules != null) {
                for (AnnotationMirror rule : rules) {
                    String ruleName = AnnotationUtils.getEnumValueName(rule, "name");
                    if (ruleName != null) {
                        try {
                            allRules.add(Validate.Rule.valueOf(ruleName));
                        } catch (IllegalArgumentException e) {
                            warn("Unknown validation rule: " + ruleName, field);
                        }
                    }
                }
            }

            // Extract parameters
            AnnotationMirror paramMirror = AnnotationUtils.getAnnotationMirror(
                field, Validate.Param.class.getCanonicalName());
            if (paramMirror != null) {
                String paramName = AnnotationUtils.getStringValue(paramMirror, "name");
                String paramValue = AnnotationUtils.getStringValue(paramMirror, "value");
                if (paramName != null && paramValue != null) {
                    allParams.put(paramName, paramValue);
                }
            }

            // Get params from the Validate annotation itself if present
            List<AnnotationMirror> params = AnnotationUtils.getAnnotationValue(validate, "params", elements);
            if (params != null) {
                for (AnnotationMirror param : params) {
                    String paramName = AnnotationUtils.getStringValue(param, "name");
                    String paramValue = AnnotationUtils.getStringValue(param, "value");
                    if (paramName != null && paramValue != null) {
                        allParams.put(paramName, paramValue);
                    }
                }
            }

            // Extract custom validator class
            if (customValidatorClass == null) {
                TypeMirror customClass = AnnotationUtils.getClassValue(validate, "custom");
                if (customClass != null) {
                    String qn = TypeMirrorUtils.qualifiedName(customClass);
                    if (!"java.lang.Void".equals(qn)) {
                        customValidatorClass = qn;
                    }
                }
            }

            // Extract message (use first non-empty message)
            if (message == null || message.isEmpty()) {
                String msg = AnnotationUtils.getStringValue(validate, "message");
                if (msg != null && !msg.isEmpty()) {
                    message = msg;
                }
            }
        }

        // Also look for standalone @Validate.Param annotations
        List<AnnotationMirror> standaloneParams = AnnotationUtils.getAnnotations(
            field, Validate.Param.class.getCanonicalName());
        for (AnnotationMirror param : standaloneParams) {
            String paramName = AnnotationUtils.getStringValue(param, "name");
            String paramValue = AnnotationUtils.getStringValue(param, "value");
            if (paramName != null && paramValue != null) {
                allParams.put(paramName, paramValue);
            }
        }

        if (allRules.isEmpty() && customValidatorClass == null) {
            return null;
        }

        return new ValidationModel(
            allRules.toArray(new Validate.Rule[0]),
            allParams,
            customValidatorClass,
            message
        );
    }

    private RelationshipModel extractManyToOne(
        VariableElement field,
        AnnotationMirror mto,
        String columnName) {
        TypeMirror target = field.asType();

        if (!(target instanceof DeclaredType dt)) {
            error("@ManyToOne field must be a declared type", field);
            return null;
        }

        TypeElement targetElement = (TypeElement) dt.asElement();

        if (targetElement.getAnnotation(Repository.class) == null) {
            error("@ManyToOne target must be a @Repository entity", field);
            return null;
        }

        return RelationshipModel.create(
            MANY_TO_ONE,
            field.getSimpleName().toString(),
            columnName,
            target,
            target,
            null,
            AnnotationUtils.getBooleanValue(mto, "lazy"),
            CollectionKind.OTHER,
            AnnotationUtils.getConsistencyValue(mto, "consistency")
        );
    }

    private CollectionKind collectionKind(TypeMirror mirror) {
        if (!(mirror instanceof DeclaredType declared)) {
            return CollectionKind.OTHER;
        }

        TypeMirror raw = types.erasure(declared);

        if (types.isAssignable(raw, deque))  return CollectionKind.DEQUE;
        if (types.isAssignable(raw, queue))  return CollectionKind.QUEUE;
        if (types.isAssignable(raw, set))    return CollectionKind.SET;
        if (types.isAssignable(raw, list))   return CollectionKind.LIST;
        if (types.isAssignable(raw, map))    return CollectionKind.MAP;

        return CollectionKind.OTHER;
    }

    private RelationshipModel extractOneToOne(
        VariableElement field,
        AnnotationMirror oto,
        String columnName) {
        TypeMirror target = field.asType();

        if (!(target instanceof DeclaredType dt)) {
            error("@OneToOne field must be a declared type", field);
            return null;
        }

        TypeElement targetElement = (TypeElement) dt.asElement();

        if (targetElement.getAnnotation(Repository.class) == null) {
            error("@OneToOne target must be a @Repository entity", field);
            return null;
        }

        String mappedBy = AnnotationUtils.getStringValue(oto, "mappedBy");
        if (mappedBy != null) {
            mappedBy = mappedBy.trim();
            if (mappedBy.isEmpty()) {
                mappedBy = null;
            }
        }

        if (mappedBy != null) {
            boolean found = false;
            for (Element e : targetElement.getEnclosedElements()) {
                if (e.getKind() != ElementKind.FIELD) continue;
                if (!e.getSimpleName().contentEquals(mappedBy)) continue;

                found = true;
                if (!(AnnotationUtils.hasAnnotation(e, ManyToOne.class.getCanonicalName())
                    || AnnotationUtils.hasAnnotation(e, OneToOne.class.getCanonicalName()))) {
                    error("@OneToOne mappedBy must refer to a ManyToOne or OneToOne field", field);
                    return null;
                }

                if (!typesMatch(e.asType(), field.getEnclosingElement().asType())) {
                    error("@OneToOne mappedBy field type does not match source entity type", field);
                    return null;
                }
                break;
            }

            if (!found) {
                error("@OneToOne mappedBy field not found on target entity: " + mappedBy, field);
                return null;
            }
        }

        return RelationshipModel.create(
            ONE_TO_ONE,
            field.getSimpleName().toString(),
            columnName,
            field.asType(),
            target,
            mappedBy,
            AnnotationUtils.getBooleanValue(oto, "lazy"),
            CollectionKind.OTHER,
            AnnotationUtils.getConsistencyValue(oto, "consistency")
        );
    }

    private RelationshipModel extractOneToMany(
        VariableElement field,
        AnnotationMirror otm,
        String columnName) {
        if (!(field.asType() instanceof DeclaredType dt)
            || dt.getTypeArguments().size() != 1) {
            error("@OneToMany field must be a generic collection", field);
            return null;
        }

        TypeMirror mappedByMirror = AnnotationUtils.getClassValue(otm, "mappedBy");
        if (mappedByMirror == null) {
            error("@OneToMany must define mappedBy", field);
            return null;
        }

        String mappedByQN = TypeMirrorUtils.qualifiedName(mappedByMirror);

        if (mappedByQN.equals("java.lang.Void")) {
            error("@OneToMany mappedBy cannot be void.class", field);
            return null;
        }

        TypeElement mappedByElement =
            (TypeElement) ((DeclaredType) mappedByMirror).asElement();

        if (mappedByElement.getAnnotation(Repository.class) == null) {
            error("mappedBy must reference a @Repository entity", field);
            return null;
        }

        boolean inverseFound = false;
        for (Element e : mappedByElement.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) continue;

            if (AnnotationUtils.hasAnnotation(e, ManyToOne.class.getCanonicalName())
                || AnnotationUtils.hasAnnotation(e, OneToOne.class.getCanonicalName())) {

                if (typesMatch(e.asType(), field.getEnclosingElement().asType())) {
                    inverseFound = true;
                    break;
                }
            }
        }

        if (!inverseFound) {
            error(
                "mappedBy entity does not contain a matching ManyToOne or OneToOne field",
                field
            );
            return null;
        }

        CollectionKind collectionKind = collectionKind(dt);

        TypeMirror elementType = dt.getTypeArguments().getFirst();

        return RelationshipModel.create(
            ONE_TO_MANY,
            field.getSimpleName().toString(),
            columnName,
            field.asType(),
            elementType,
            mappedByQN,
            AnnotationUtils.getBooleanValue(otm, "lazy"),
            collectionKind,
            AnnotationUtils.getConsistencyValue(otm, "consistency")
        );
    }

    private boolean typesMatch(TypeMirror a, TypeMirror b) {
        if (a == null || b == null) return false;

        TypeMirror ea = types.erasure(a);
        TypeMirror eb = types.erasure(b);

        return types.isSameType(ea, eb)
            || types.isAssignable(ea, eb)
            || types.isAssignable(eb, ea);
    }

    private static List<ConstraintModel> extractConstraints(TypeElement entity) {
        List<ConstraintModel> out = new ArrayList<>(4);

        for (AnnotationMirror am : AnnotationUtils.getAnnotations(entity, Constraint.class.getCanonicalName())) {
            out.add(parseConstraint(am));
        }

        for (Element e : entity.getEnclosedElements()) {
            for (AnnotationMirror am : AnnotationUtils.getAnnotations(e, Constraint.class.getCanonicalName())) {
                out.add(parseConstraint(am));
            }
        }

        return out;
    }

    private static ConstraintModel parseConstraint(AnnotationMirror am) {
        String name = null;

        Set<? extends Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> entries = am.getElementValues().entrySet();
        List<String> fields = new ArrayList<>(entries.size());

        for (var e : entries) {
            String k = e.getKey().getSimpleName().toString();
            Object v = e.getValue().getValue();

            if (k.equals(ANN_KEY_NAME)) {
                name = v.toString();
            } else if (k.equals(ANN_KEY_FIELDS)) {
                fields = stringifyResult((List<AnnotationValue>) v);
            }
        }

        return new ConstraintModel(name, fields);
    }

    private void validateConstraints(
        List<ConstraintModel> constraints,
        Set<String> validFields,
        Element target
    ) {
        Set<String> names = new HashSet<>(16);

        for (ConstraintModel c : constraints) {
            if (!names.add(c.name())) {
                error("Duplicate constraint: " + c.name(), target);
            }

            for (String f : c.fields()) {
                if (!validFields.contains(f)) {
                    error("Constraint refers to unknown field: " + f, target);
                }
            }
        }
    }

    private void validateIndexes(
        List<io.github.flameyossnowy.universal.checker.IndexModel> indexes,
        Set<String> validFields,
        Element target
    ) {
        Set<String> names = new HashSet<>(16);

        for (io.github.flameyossnowy.universal.checker.IndexModel idx : indexes) {
            if (!names.add(idx.name())) {
                error("Duplicate index name: " + idx.name(), target);
            }

            for (String f : idx.fields()) {
                if (!validFields.contains(f)) {
                    error("Index refers to unknown field: " + f, target);
                }
            }
        }
    }

    private List<io.github.flameyossnowy.universal.checker.IndexModel> extractIndexes(TypeElement entity) {
        List<io.github.flameyossnowy.universal.checker.IndexModel> out = new ArrayList<>(4);

        for (AnnotationMirror am : AnnotationUtils.getAnnotations(entity, Index.class.getCanonicalName())) {
            String name = null;
            List<String> fields = List.of();
            IndexType type = IndexType.NORMAL;

            for (var e : am.getElementValues().entrySet()) {
                String key = e.getKey().getSimpleName().toString();
                Object v = e.getValue().getValue();

                switch (key) {
                    case ANN_KEY_NAME   -> name = v.toString();
                    case ANN_KEY_FIELDS -> {
                        @SuppressWarnings("unchecked")
                        List<AnnotationValue> value = (List<AnnotationValue>) v;
                        fields = stringifyResult(value);
                    }
                    case ANN_KEY_TYPE -> type = IndexType.valueOf(v.toString());
                }
            }

            if (name == null || name.isBlank()) {
                error("@Index name cannot be empty", entity);
            }

            out.add(new io.github.flameyossnowy.universal.checker.IndexModel(name, fields, type));
        }

        return out;
    }

    /**
     * Extracts field-level @Index annotations from entity fields.
     * For each field annotated with @Index, creates an index model that indexes just that field.
     *
     * @param entity the entity type element
     * @param fields the list of enclosed fields
     * @return list of index models created from field-level @Index annotations
     */
    private List<io.github.flameyossnowy.universal.checker.IndexModel> extractFieldIndexes(
            TypeElement entity,
            List<VariableElement> fields) {

        List<io.github.flameyossnowy.universal.checker.IndexModel> out = new ArrayList<>(4);
        String tableName = entity.getAnnotation(Repository.class).name();

        for (VariableElement field : fields) {
            for (AnnotationMirror am : AnnotationUtils.getAnnotations(field, Index.class.getCanonicalName())) {
                String name = null;
                IndexType type = IndexType.NORMAL;

                for (var e : elements.getElementValuesWithDefaults(am).entrySet()) {
                    String key = e.getKey().getSimpleName().toString();
                    Object v = e.getValue().getValue();

                    switch (key) {
                        case ANN_KEY_NAME   -> name = v.toString();
                        case ANN_KEY_TYPE   -> type = IndexType.valueOf(v.toString());
                        // fields() is ignored for field-level indexes - the annotated field is used
                    }
                }

                String fieldName = field.getSimpleName().toString();

                // Auto-generate index name if not provided
                if (name == null || name.isBlank()) {
                    name = "idx_" + tableName + "_" + fieldName;
                }

                out.add(new io.github.flameyossnowy.universal.checker.IndexModel(name, List.of(fieldName), type));
            }
        }

        return out;
    }

    private static List<String> stringifyResult(List<AnnotationValue> v) {
        List<String> fields = new ArrayList<>(16);
        for (AnnotationValue x : v) fields.add(x.getValue().toString());
        return fields;
    }

    private void error(String msg, Element e) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    private void warn(String msg, Element e) {
        messager.printMessage(Diagnostic.Kind.WARNING, msg, e);
    }
}