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
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.CacheConfig;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.meta.JsonIndexModel;
import io.github.flameyossnowy.universal.api.meta.JsonStorageKind;
import io.github.flameyossnowy.universal.api.resolver.ResolveWith;
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
})
public class RepositoryValidatorProcessor extends AbstractProcessor {
    private Types types;
    private Messager messager;
    private Elements elements;
    private Filer filer;

    private static final String RESOURCE =
        "META-INF/universal/models.list";

    private final List<String> qualifiedNames = new ArrayList<>(16);

    private final Set<String> repositoryNames = new HashSet<>(16);

    private TypeMirror map;
    private TypeMirror list;
    private TypeMirror set;
    private TypeMirror queue;
    private TypeMirror deque;

    private static final List<String> FIELD_ANNOTATIONS = List.of(
        ManyToOne.class.getCanonicalName(),
        OneToOne.class.getCanonicalName(),
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

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();

        Elements elements = processingEnv.getElementUtils();
        this.elements = elements;

        this.map = TypeMirrorUtils.typeOf(Map.class, elements);
        this.list = TypeMirrorUtils.typeOf(List.class, elements);
        this.set = TypeMirrorUtils.typeOf(Set.class, elements);
        this.queue = TypeMirrorUtils.typeOf(Queue.class, elements);
        this.deque = TypeMirrorUtils.typeOf(Deque.class, elements);
    }

    private final Map<String, ResolverInfo> globalTypeResolvers = new HashMap<>();

    private record ResolverInfo(String resolverClassName, int priority) {
    }

    private void scanResolverAnnotations(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Resolves.class)) {
            if (!(element instanceof TypeElement resolverClass)) {
                continue;
            }

            // Verify it implements TypeResolver
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

            // Extract types and priority
            List<TypeMirror> handledTypes = new ArrayList<>();
            int priority = 0;

            for (var entry : resolvesMirror.getElementValues().entrySet()) {
                String key = entry.getKey().getSimpleName().toString();

                if ("value".equals(key)) {
                    @SuppressWarnings("unchecked")
                    List<AnnotationValue> values = (List<AnnotationValue>) entry.getValue().getValue();
                    for (AnnotationValue val : values) {
                        TypeMirror type = (TypeMirror) val.getValue();
                        handledTypes.add(type);
                    }
                } else if ("priority".equals(key)) {
                    priority = (int) entry.getValue().getValue();
                }
            }

            String resolverQualifiedName = resolverClass.getQualifiedName().toString();

            // Register each type
            for (TypeMirror handledType : handledTypes) {
                String typeQualifiedName = TypeMirrorUtils.qualifiedName(handledType);

                ResolverInfo existing = globalTypeResolvers.get(typeQualifiedName);
                if (existing != null) {
                    if (priority > existing.priority) {
                        // Higher priority wins
                        globalTypeResolvers.put(
                            typeQualifiedName,
                            new ResolverInfo(resolverQualifiedName, priority)
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
                        new ResolverInfo(resolverQualifiedName, priority)
                    );
                }
            }
        }
    }

    /**
     * Check if a TypeElement implements TypeResolver interface.
     */
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

        // Check superclass recursively
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

            handleRepository(type, type.getKind());

            boolean hasNoArgCtor = false;
            boolean hasId = false;
            boolean hasRelationship = false;

            for (Element e : type.getEnclosedElements()) {
                if (e.getKind() == ElementKind.CONSTRUCTOR) {
                    if (((ExecutableElement) e).getParameters().isEmpty()) {
                        hasNoArgCtor = true;
                    }
                }

                if (e.getKind() == ElementKind.FIELD) {
                    if (AnnotationUtils.hasAnnotation(e, Id.class.getCanonicalName())) hasId = true;
                    if (AnnotationUtils.hasAnnotation(e, OneToOne.class.getCanonicalName())
                        || AnnotationUtils.hasAnnotation(e, OneToMany.class.getCanonicalName()) ||
                        AnnotationUtils.hasAnnotation(e, ManyToOne.class.getCanonicalName())) {
                        hasRelationship = true;
                    }

                    handleField((VariableElement) e);
                }
            }

            if (hasRelationship && !hasId) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@Repository " + type.getSimpleName() +
                        " has relationship fields but no @Id field.",
                    type);
            }

            if (!hasNoArgCtor && type.getKind() != ElementKind.RECORD) {
                messager.printMessage(Diagnostic.Kind.ERROR,
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

    private void handleRepository(TypeElement element, ElementKind kind) {
        if (!kind.isClass()) return;

        Repository repo = element.getAnnotation(Repository.class);
        if (repo == null) return;

        if (repo.name().isBlank()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository name cannot be empty", element);
            return;
        }

        if (!repositoryNames.add(repo.name())) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository name must be unique", element);
        }

        if (element.getModifiers().contains(Modifier.ABSTRACT)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository cannot be abstract", element);
        }

        if (kind == ElementKind.INTERFACE || kind == ElementKind.ENUM) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@Repository cannot be enum or interface", element);
        }

        checkIndexAndConstraintReferences(element);
        checkAccessors(element);

        RepositoryModel model = buildRepositoryModel(element);

        Set<String> fieldNames = new HashSet<>(16);
        for (FieldModel field : model.fields()) {
            fieldNames.add(field.name());
        }

        validateIndexes(model.indexes(), fieldNames, element);
        validateConstraints(model.constraints(), fieldNames, element);
        UnifiedFactoryGenerator gen = new UnifiedFactoryGenerator(processingEnv, processingEnv.getFiler());
        gen.generate(model);
        qualifiedNames.addAll(gen.getQualifiedNames());
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

    private void checkAccessors(TypeElement entity) {
        if (entity.getKind() == ElementKind.RECORD) return;

        Map<String, ExecutableElement> methods = new HashMap<>(16);

        for (Element e : entity.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                methods.put(e.getSimpleName().toString(), (ExecutableElement) e);
            }
        }

        for (Element e : entity.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) continue;

            VariableElement field = (VariableElement) e;
            String name = field.getSimpleName().toString();
            String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);

            ExecutableElement getter = methods.get("get" + cap);
            if (getter == null || !getter.getParameters().isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Missing getter get" + cap + "() for field " + name, field);
            }

            if (!field.getModifiers().contains(Modifier.FINAL)) {
                ExecutableElement setter = methods.get("set" + cap);
                if (setter == null || setter.getParameters().size() != 1) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "Missing setter set" + cap + "(..) for field " + name, field);
                }
            }
        }
    }

    private void checkIndexAndConstraintReferences(TypeElement type) {
        Set<String> fields = new HashSet<>(16);

        for (Element e : type.getEnclosedElements()) {
            if (e.getKind() == ElementKind.FIELD) {
                fields.add(e.getSimpleName().toString());
            }
        }

        for (Element e : type.getEnclosedElements()) {
            for (AnnotationMirror am : AnnotationUtils.getAnnotations(e, Index.class.getCanonicalName())) {
                validateColumns("Index", AnnotationUtils.getStringArrayValue(am), fields, e);
            }
            for (AnnotationMirror am : AnnotationUtils.getAnnotations(e, Constraint.class.getCanonicalName())) {
                validateColumns("Constraint", AnnotationUtils.getStringArrayValue(am), fields, e);
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
                messager.printMessage(Diagnostic.Kind.ERROR,
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
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Final fields are not allowed", field);
        }

        checkRelationshipFieldAnnotations(field);
        checkRawTypes(field);
    }

    private void checkFieldAnnotations(VariableElement field) {
        for (String ann : FIELD_ANNOTATIONS) {
            if (AnnotationUtils.hasAnnotation(field, ann)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field " + field.getSimpleName() +
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
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Multiple relationship annotations", field);
        }
    }

    private void checkRawTypes(VariableElement field) {
        if (!(field.asType() instanceof DeclaredType dt)) return;

        TypeElement te = (TypeElement) dt.asElement();

        if (te.getTypeParameters().isEmpty()) {
            return;
        }

        if (dt.getTypeArguments().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Raw generic type not allowed - please specify type parameters", field);
        }
    }

    private RepositoryModel buildRepositoryModel(TypeElement entity) {
        String packageName =
            elements.getPackageOf(entity).getQualifiedName().toString();

        Repository repo = entity.getAnnotation(Repository.class);
        FetchPageSize fetchPageSize = entity.getAnnotation(FetchPageSize.class);

        // Extract entity-level caching annotations
        Cacheable cacheable = entity.getAnnotation(Cacheable.class);
        GlobalCacheable globalCacheable = entity.getAnnotation(GlobalCacheable.class);

        CacheConfig cacheConfig = CacheConfig.none();
        if (cacheable != null) {
            cacheConfig = new CacheConfig(
                cacheable.maxCacheSize(),
                cacheable.algorithm()
            );
        }

        List<FieldModel> fields = new ArrayList<>(16);
        List<FieldModel> primaryKeys = new ArrayList<>(16);

        List<io.github.flameyossnowy.universal.checker.IndexModel> indexes = extractIndexes(entity);
        Set<io.github.flameyossnowy.universal.checker.IndexModel> indexModelSet = new LinkedHashSet<>(indexes);

        List<ConstraintModel> constraints = extractConstraints(entity);
        List<RelationshipModel> relationships = new ArrayList<>(8);

        TypeMirror idType = null;

        for (Element enclosed : entity.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;

            VariableElement field = (VariableElement) enclosed;

            // ========== Basic Metadata ==========
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
                    TypeMirror providerType = AnnotationUtils.getClassValue(providerMirror, "value");
                    if (providerType != null) {
                        defaultValueProviderClass = TypeMirrorUtils.qualifiedName(providerType);

                        // Validate it's not void.class
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

            if (hasNow) {
                String typeName = type.toString();
                if (!typeName.contains("LocalDateTime") &&
                    !typeName.contains("LocalDate") &&
                    !typeName.contains("Instant") &&
                    !typeName.contains("ZonedDateTime") &&
                    !typeName.contains("OffsetDateTime") &&
                    !typeName.contains("Date") &&
                    !typeName.contains("Timestamp")) {
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

            Condition condition = field.getAnnotation(Condition.class);
            OnDelete onDelete = field.getAnnotation(OnDelete.class);
            OnUpdate onUpdate = field.getAnnotation(OnUpdate.class);

            String resolveWithClass = null;
            ResolveWith resolveWith = field.getAnnotation(ResolveWith.class);
            if (resolveWith != null) {
                AnnotationMirror resolveWithMirror = AnnotationUtils.getAnnotationMirror(
                    field,
                    ResolveWith.class.getCanonicalName()
                );
                if (resolveWithMirror != null) {
                    TypeMirror resolverType = AnnotationUtils.getClassValue(resolveWithMirror, "value");
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

            TypeMirror elementType = resolveElementTypeMirror(type);
            TypeMirror mapKeyType = resolveMapKeyTypeMirror(type);
            TypeMirror mapValueType = resolveMapValueTypeMirror(type);

            boolean indexed = checkIfIndexed(indexModelSet, fieldName);

            CollectionKind collectionKind = CollectionKind.OTHER;

            if (TypeMirrorUtils.isList(types, elements, type)) {
                collectionKind = CollectionKind.LIST;
            } else if (TypeMirrorUtils.isSet(types, elements, type)) {
                collectionKind = CollectionKind.SET;
            } else if (TypeMirrorUtils.isMap(types, elements, type)) {
                if (TypeMirrorUtils.isCollectionValueMap(types, elements, type)) {
                    collectionKind = CollectionKind.MULTIMAP; // Map<K, Collection<V>>
                } else {
                    collectionKind = CollectionKind.MAP;
                }
            }

            RelationshipModel rel = extractRelationship(field);
            if (rel != null) {
                relationships.add(rel);
            }

            AnnotationMirror jsonField = AnnotationUtils.getAnnotationMirror(field, JsonField.class.getCanonicalName());
            boolean isJson = jsonField != null;
            JsonStorageKind jsonStorageKind = JsonStorageKind.COLUMN;
            String jsonColumnDefinition = null;
            String jsonCodecClass = null;
            boolean jsonQueryable = false;
            boolean jsonPartialUpdate = false;

            if (isJson) {
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
                jsonIndexes = new java.util.ArrayList<>(jsonIndexMirrors.size());
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
                "get" + capitalized,
                "set" + capitalized,
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

        TypeMirror auditLoggerType = AnnotationUtils.getClassValue(auditLogger, "value");
        TypeMirror exceptionHandlerType = AnnotationUtils.getClassValue(exceptionHandler, "value");
        TypeMirror lifecycleListenerType = AnnotationUtils.getClassValue(lifecycleListener, "value");

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
            lifecycleListenerType
        );
    }

    private boolean checkIfIndexed(Collection<io.github.flameyossnowy.universal.checker.IndexModel> indexes, String fieldName) {
        for (io.github.flameyossnowy.universal.checker.IndexModel index : indexes) {
            if (index.fields().contains(fieldName)) {
                return true;
            }
        }

        return false;
    }

    private TypeMirror resolveElementTypeMirror(TypeMirror type) {
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

    private TypeMirror resolveMapKeyTypeMirror(TypeMirror type) {
        if (type instanceof DeclaredType dt) {
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (!args.isEmpty() && dt.asElement().toString().equals("java.util.Map")) {
                return args.getFirst();
            }
        }
        return null;
    }

    private TypeMirror resolveMapValueTypeMirror(TypeMirror type) {
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

        int count = (otm != null ? 1 : 0)
            + (mto != null ? 1 : 0)
            + (oto != null ? 1 : 0);

        if (count == 0) return null;

        if (count > 1) {
            error("Field may only have one relationship annotation", field);
            return null;
        }

        if (otm != null) return extractOneToMany(field, otm);
        if (mto != null) return extractManyToOne(field, mto);
        return extractOneToOne(field, oto);
    }

    private RelationshipModel extractManyToOne(
        VariableElement field,
        AnnotationMirror mto
    ) {
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
            target,
            target,
            null,
            AnnotationUtils.getBooleanValue(mto, "lazy"),
            CollectionKind.OTHER,
            AnnotationUtils.getConsistencyValue(mto, "consistency")
        );
    }

    private CollectionKind collectionKind(
        TypeMirror mirror
    ) {
        if (!(mirror instanceof DeclaredType declared)) {
            return CollectionKind.OTHER;
        }

        TypeMirror raw = types.erasure(declared);

        if (types.isAssignable(raw, deque)) {
            return CollectionKind.DEQUE;
        }
        if (types.isAssignable(raw, queue)) {
            return CollectionKind.QUEUE;
        }
        if (types.isAssignable(raw, set)) {
            return CollectionKind.SET;
        }

        if (types.isAssignable(raw, list)) {
            return CollectionKind.LIST;
        }

        if (types.isAssignable(raw, map)) {
            return CollectionKind.MAP;
        }

        return CollectionKind.OTHER;
    }

    private RelationshipModel extractOneToOne(
        VariableElement field,
        AnnotationMirror oto
    ) {
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
            target,
            target,
            mappedBy,
            AnnotationUtils.getBooleanValue(oto, "lazy"),
            CollectionKind.OTHER,
            AnnotationUtils.getConsistencyValue(oto, "consistency")
        );
    }

    private RelationshipModel extractOneToMany(
        VariableElement field,
        AnnotationMirror otm
    ) {
        // Must be a parameterized collection
        if (!(field.asType() instanceof DeclaredType dt)
            || dt.getTypeArguments().size() != 1) {
            error("@OneToMany field must be a generic collection", field);
            return null;
        }

        // mappedBy
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

        // mappedBy must be a @Repository
        TypeElement mappedByElement =
            (TypeElement) ((DeclaredType) mappedByMirror).asElement();

        if (mappedByElement.getAnnotation(Repository.class) == null) {
            error("mappedBy must reference a @Repository entity", field);
            return null;
        }

        // Validate inverse field exists
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
            field.asType(),   // ← List<Faction>
            elementType,      // ← Faction
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

    private List<ConstraintModel> extractConstraints(TypeElement entity) {
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
        List<String> fields = List.of();

        for (var e : am.getElementValues().entrySet()) {
            String k = e.getKey().getSimpleName().toString();
            Object v = e.getValue().getValue();

            if (k.equals("name")) {
                name = v.toString();
            } else if (k.equals("fields")) {
                @SuppressWarnings("unchecked")
                List<AnnotationValue> vals = (List<AnnotationValue>) v;
                List<String> result = new ArrayList<>();
                for (AnnotationValue x : vals) {
                    result.add(x.getValue().toString());
                }
                fields = result;
            }
        }

        return new ConstraintModel(name, fields);
    }

    private void validateConstraints(
        List<ConstraintModel> constraints,
        Set<String> validFields,
        Element target
    ) {
        Set<String> names = new HashSet<>();

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
        Set<String> names = new HashSet<>();

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
                    case "name" -> name = v.toString();
                    case "fields" -> {
                        @SuppressWarnings("unchecked")
                        List<AnnotationValue> vals = (List<AnnotationValue>) v;
                        List<String> result = new ArrayList<>();
                        for (AnnotationValue x : vals) {
                            String string = x.getValue().toString();
                            result.add(string);
                        }
                        fields = result;
                    }
                    case "type" -> type = IndexType.valueOf(v.toString());
                }
            }

            if (name == null || name.isBlank()) {
                error("@Index name cannot be empty", entity);
            }

            out.add(new io.github.flameyossnowy.universal.checker.IndexModel(name, fields, type));
        }

        return out;
    }

    private void error(String msg, Element e) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }
}