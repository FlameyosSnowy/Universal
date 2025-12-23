package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.defvalues.DefaultTypeProvider;
import io.github.flameyossnowy.universal.api.exceptions.ConstructorThrewException;

import me.sunlan.fastreflection.FastField;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
@ApiStatus.Internal
public class RepositoryMetadata {
    private static final Map<Class<?>, RepositoryInformation> cache = new ConcurrentHashMap<>(3);

    private static final Set<Class<?>> ALLOWED_ID_TYPES = Set.of(String.class, Long.class, Integer.class, UUID.class);
    private static final Set<Class<?>> NUMERIC_ID_TYPES = Set.of(Long.class, Integer.class);

    private RepositoryMetadata() {}

    /**
     * Retrieves the metadata information for a given entity class.
     *
     * <p>This method returns a {@link RepositoryInformation} instance corresponding
     * to the specified entity class. If the metadata for the entity class is not
     * already cached, it will be built and stored in the cache for future access.
     *
     * @param entityClass the class for which to retrieve repository metadata
     * @return the {@link RepositoryInformation} instance for the entity class,
     *         or {@code null} if the class is not annotated as a repository.
     */
    public static @Nullable RepositoryInformation getMetadata(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, RepositoryMetadata::buildMetadata);
    }

    private static @Nullable RepositoryInformation buildMetadata(@NotNull Class<?> entityClass) {
        Repository repositoryAnnotation = entityClass.getAnnotation(Repository.class);
        if (repositoryAnnotation == null) return null;

        if (entityClass.getSuperclass().isAnnotationPresent(Repository.class)) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " is annotated with @Repository and has a superclass also annotated with @Repository");
        }

        Field[] fields = entityClass.getDeclaredFields();
        RecordComponent[] recordComponents = entityClass.isRecord() ? entityClass.getRecordComponents() : new RecordComponent[0];

        if (fields.length == 0 && recordComponents.length == 0) {
            throw new IllegalArgumentException("Class " + entityClass.getSimpleName() + " is annotated with @Repository but has no fields or record components");
        }

        String tableName = repositoryAnnotation.name();
        Constraint[] constraints = entityClass.getAnnotationsByType(Constraint.class);
        Index[] indexes = entityClass.getAnnotationsByType(Index.class);
        Cacheable cacheable = entityClass.getAnnotation(Cacheable.class);
        GlobalCacheable globalCacheable = entityClass.getAnnotation(GlobalCacheable.class);
        FetchPageSize fetchPageSize = entityClass.getAnnotation(FetchPageSize.class);
        RepositoryAuditLogger repositoryAuditLogger = entityClass.getAnnotation(RepositoryAuditLogger.class);
        RepositoryExceptionHandler repositoryExceptionHandler = entityClass.getAnnotation(RepositoryExceptionHandler.class);
        RepositoryEventLifecycleListener repositoryEventLifecycleListener = entityClass.getAnnotation(RepositoryEventLifecycleListener.class);

        Set<String> indexedFields = Arrays.stream(indexes).map(Index::fields).flatMap(Arrays::stream).collect(Collectors.toUnmodifiableSet());

        int length = fields.length + recordComponents.length;
        Map<String, FieldData<?>> data = new LinkedHashMap<>(length);
        Map<String, FieldData<?>> oneToManyCache = new LinkedHashMap<>(length);
        Map<String, FieldData<?>> manyToOneCache = new LinkedHashMap<>(length);
        Map<String, FieldData<?>> oneToOneCache = new LinkedHashMap<>(length);

        RepositoryInformation information = getInformation(
                entityClass, tableName, constraints,
                indexes, cacheable, fetchPageSize, data,
                oneToManyCache, manyToOneCache, oneToOneCache, repositoryAuditLogger,
                repositoryEventLifecycleListener, repositoryExceptionHandler, globalCacheable
        );

        if (recordComponents.length == 0) processFields(fields, information, tableName, data);

        if (recordComponents.length > 0) processRecordComponents(recordComponents, information, tableName, data);

        for (FieldData<?> fieldData : data.values()) {
            fieldData.setIndexed(indexedFields.contains(fieldData.name()));

            if (fieldData.primary()) {
                information.addPrimaryKey(fieldData);
            }

            if (fieldData.oneToOne() != null) {
                oneToOneCache.put(fieldData.name(), fieldData);
            }

            if (fieldData.oneToMany() != null) {
                oneToManyCache.put(fieldData.name(), fieldData);
            }

            if (fieldData.manyToOne() != null) {
                manyToOneCache.put(fieldData.name(), fieldData);
            }

        }

        return information;
    }

    private static void processRecordComponents(RecordComponent[] recordComponents, RepositoryInformation information, String tableName, Map<String, FieldData<?>> data) {
        for (RecordComponent recordComponent : recordComponents) {
            processRecordComponent(information, recordComponent, tableName, data);
        }
    }

    private static void processFields(Field[] fields, RepositoryInformation information, String tableName, Map<String, FieldData<?>> data) {
        for (Field field : fields) {
            processField(information, field, tableName, data);
        }
    }

    private static void processField(@NotNull RepositoryInformation information, Field field, String tableName, Map<String, FieldData<?>> data) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods) || Modifier.isFinal(mods) || Modifier.isTransient(mods)) return;

        String name = field.getName();
        FieldData<?> fieldData = createFieldData(information, field, tableName, name);
        data.put(name, fieldData);
    }

    private static void processRecordComponent(@NotNull RepositoryInformation information, RecordComponent recordComponent, String tableName, Map<String, FieldData<?>> data) {
        String name = recordComponent.getName();
        FieldData<?> fieldData = createFieldData(information, recordComponent, tableName, name);
        data.put(name, fieldData);
    }

    private static @NotNull RepositoryInformation getInformation(
            @NotNull Class<?> entityClass,
            String tableName,
            Constraint[] constraints,
            Index[] indexes,
            Cacheable cacheable,
            FetchPageSize fetchPageSize,
            Map<String, FieldData<?>> data,
            Map<String, FieldData<?>> oneToManyCache,
            Map<String, FieldData<?>> manyToOneCache,
            Map<String, FieldData<?>> oneToOneCache,
            RepositoryAuditLogger repositoryAuditLogger,
            RepositoryEventLifecycleListener repositoryEventLifecycleListener,
            RepositoryExceptionHandler repositoryExceptionHandler,
            GlobalCacheable globalCacheable) {
        try {
            return new RepositoryInformation(
                    tableName, constraints, indexes, cacheable, entityClass,
                    fetchPageSize == null ? -1 : fetchPageSize.value(), data, oneToManyCache, manyToOneCache, oneToOneCache,

                    repositoryAuditLogger == null ? null : repositoryAuditLogger.value().getDeclaredConstructor().newInstance(),
                    repositoryEventLifecycleListener == null ? null : repositoryEventLifecycleListener.value().getDeclaredConstructor().newInstance(),
                    repositoryExceptionHandler == null ? null : repositoryExceptionHandler.value().getDeclaredConstructor().newInstance(),

                    globalCacheable
            );
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static <T> FieldData<T> createFieldData(@NotNull RepositoryInformation information, Object fieldOrRecordComponent, String tableName, String fieldName) {
        if (fieldOrRecordComponent instanceof Field field) {
            return createFieldDataFromField(information, field, tableName, fieldName);
        } else if (fieldOrRecordComponent instanceof RecordComponent recordComponent) {
            return createFieldDataFromRecordComponent(information, recordComponent, tableName, fieldName);
        } else {
            throw new IllegalArgumentException("Unsupported field or record component: " + fieldOrRecordComponent);
        }
    }

    private static <T> @NotNull FieldData<T> createFieldDataFromField(@NotNull RepositoryInformation information, @NotNull Field field, String tableName, String fieldName) {
        Named name = field.getAnnotation(Named.class);
        DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
        DefaultValueProvider defaultValueProvider = field.getAnnotation(DefaultValueProvider.class);
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        ExternalRepository externalRepository = field.getAnnotation(ExternalRepository.class);

        if (oneToOne != null || manyToOne != null || externalRepository != null || oneToMany != null) {
            information.setHasRelationships(true);
        }

        boolean id = field.isAnnotationPresent(Id.class);
        boolean autoIncrement = isAutoIncrement(field.getType(), field.getAnnotation(AutoIncrement.class), id);
        return new FieldData<>(
                information,
                name == null ? fieldName : name.value(),
                fieldName,
                tableName,
                FastField.create(field, true),
                field,
                (Class<T>) field.getType(),
                id, autoIncrement,
                field.isAnnotationPresent(NonNull.class),
                field.isAnnotationPresent(Unique.class),
                field.isAnnotationPresent(Now.class),
                field.getAnnotation(Constraint.class),
                field.getAnnotation(Condition.class),
                field.getAnnotation(OnUpdate.class),
                field.getAnnotation(OnDelete.class),
                oneToMany, manyToOne, oneToOne, externalRepository,
                resolveDefaultValue(defaultValue, defaultValueProvider)
        );
    }

    private static boolean isAutoIncrement(Class<?> type, AutoIncrement autoIncrement, boolean id) {
        if (id && !ALLOWED_ID_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported id type: " + type.getName());
        if (autoIncrement != null && !NUMERIC_ID_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported auto increment type: " + type.getName());
        return NUMERIC_ID_TYPES.contains(type) || autoIncrement != null;
    }

    private static <T> FieldData<T> createFieldDataFromRecordComponent(@NotNull RepositoryInformation information, RecordComponent recordComponent, String tableName, String fieldName) {
        Named name = recordComponent.getAnnotation(Named.class);
        DefaultValue defaultValue = recordComponent.getAnnotation(DefaultValue.class);
        DefaultValueProvider defaultValueProvider = recordComponent.getAnnotation(DefaultValueProvider.class);
        OneToMany oneToMany = recordComponent.getAnnotation(OneToMany.class);
        ManyToOne manyToOne = recordComponent.getAnnotation(ManyToOne.class);
        OneToOne oneToOne = recordComponent.getAnnotation(OneToOne.class);
        ExternalRepository externalRepository = recordComponent.getAnnotation(ExternalRepository.class);

        if (oneToOne != null || manyToOne != null || externalRepository != null || oneToMany != null) {
            information.setHasRelationships(true);
        }

        boolean id = recordComponent.isAnnotationPresent(Id.class);
        boolean autoIncrement = RepositoryMetadata.isAutoIncrement(
                recordComponent.getType(),
                recordComponent.getAnnotation(AutoIncrement.class),
                id
        );

        return new FieldData<>(
                information, name == null ? fieldName : name.value(), fieldName, tableName, recordComponent,
                (Class<T>) recordComponent.getType(),
                id, autoIncrement,
                recordComponent.isAnnotationPresent(NonNull.class),
                recordComponent.isAnnotationPresent(Unique.class),
                recordComponent.isAnnotationPresent(Now.class),
                recordComponent.getAnnotation(Constraint.class),
                recordComponent.getAnnotation(Condition.class),
                recordComponent.getAnnotation(OnUpdate.class),
                recordComponent.getAnnotation(OnDelete.class),
                oneToMany, manyToOne, oneToOne, externalRepository,
                resolveDefaultValue(defaultValue, defaultValueProvider)
        );
    }

    private static @Nullable Object resolveDefaultValue(DefaultValue defaultValue, DefaultValueProvider provider) {
        if (defaultValue != null) return defaultValue.value();
        if (provider == null) return null;

        try {
            Class<?> clazz = provider.value();
            Object instance = clazz.getDeclaredConstructor().newInstance(); // executed 1 time
            if (!(instance instanceof DefaultTypeProvider<?> typeProvider)) {
                throw new IllegalArgumentException("Class " + clazz.getSimpleName() + " does not implement DefaultTypeProvider");
            }
            return typeProvider.supply();
        } catch (InvocationTargetException e) {
            throw new ConstructorThrewException(e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
