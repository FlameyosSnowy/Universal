package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.defvalues.DefaultTypeProvider;
import me.sunlan.fastreflection.FastField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class RepositoryMetadata {
    private static final Map<Class<?>, RepositoryInformation> cache = new ConcurrentHashMap<>();

    // will continue collections.
    private static final Set<Class<?>> DISALLOWED_REPOSITORIES = new HashSet<>(Set.of(
            List.class,
            Set.class,
            Map.class,
            UUID.class,
            Integer.class,
            Double.class,
            Long.class,
            Float.class,
            Short.class,
            Byte.class,
            Character.class,
            Boolean.class,
            String.class,
            Date.class,
            Instant.class,
            java.sql.Date.class,
            java.sql.Time.class,
            java.sql.Timestamp.class,
            LocalDate.class,
            LocalTime.class,
            LocalDateTime.class,
            ZonedDateTime.class,
            OffsetDateTime.class,
            OffsetTime.class,
            Year.class,
            YearMonth.class,
            MonthDay.class,
            BigDecimal.class,
            BigInteger.class,
            Class.class,
            URI.class,
            URL.class,
            InetAddress.class,
            Enum.class,
            int.class,
            long.class,
            double.class,
            float.class,
            short.class,
            byte.class,
            char.class,
            boolean.class
    ));

    private RepositoryMetadata() {}

    public static @Nullable RepositoryInformation getMetadata(Class<?> entityClass) {
        // optimization
        if (DISALLOWED_REPOSITORIES.contains(entityClass)) return null;

        RepositoryInformation information = cache.get(entityClass);
        if (information == null) {
            information = buildMetadata(entityClass);
            if (information != null) cache.put(entityClass, information);
            else DISALLOWED_REPOSITORIES.add(entityClass);
        }
        return information;
    }

    private static @Nullable RepositoryInformation buildMetadata(@NotNull Class<?> entityClass) {
        Repository repositoryAnnotation = entityClass.getAnnotation(Repository.class);
        if (repositoryAnnotation == null) return null;

        if (entityClass.getSuperclass().isAnnotationPresent(Repository.class)) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " is annotated with @Repository and has a superclass also annotated with @Repository");
        }

        Field[] fields = entityClass.getDeclaredFields();
        if (fields.length == 0) {
            throw new IllegalArgumentException("Class " + entityClass.getSimpleName() + " is annotated with @Repository but has no fields");
        }

        String tableName = repositoryAnnotation.name();
        Constraint[] constraints = entityClass.getAnnotationsByType(Constraint.class);
        Index[] indexes = entityClass.getAnnotationsByType(Index.class);
        Cacheable cacheable = entityClass.getAnnotation(Cacheable.class);
        GlobalCacheable globalCacheable = entityClass.getAnnotation(GlobalCacheable.class);
        FetchPageSize fetchPageSize = entityClass.getAnnotation(FetchPageSize.class);
        RepositoryAuditLogger repositoryAuditLogger = entityClass.getAnnotation(RepositoryAuditLogger.class);
        RepositoryEventLifecycleListener repositoryEventLifecycleListener = entityClass.getAnnotation(RepositoryEventLifecycleListener.class);

        int length = fields.length;
        Map<String, FieldData<?>> data = new LinkedHashMap<>(length);
        Map<String, FieldData<?>> oneToManyCache = new LinkedHashMap<>(length);
        Map<String, FieldData<?>> manyToOneCache = new LinkedHashMap<>(length);

        Class<?>[] types = new Class<?>[length];

        RepositoryInformation information = getInformation(
                entityClass, tableName, constraints,
                indexes, cacheable, types, fetchPageSize, data,
                oneToManyCache, manyToOneCache, repositoryAuditLogger,
                repositoryEventLifecycleListener, globalCacheable
        );

        for (int index = 0; index < length; index++) {
            Field field = fields[index];

            if (Modifier.isStatic(field.getModifiers())) continue;
            if (Modifier.isFinal(field.getModifiers())) continue;

            String name = field.getName();

            FieldData<?> fieldData = createFieldData(information, field, tableName, name);
            data.put(field.getName(), fieldData);
            types[index] = fieldData.type();

            if (fieldData.primary()) {
                if (information.getPrimaryKey() != null) throw new IllegalArgumentException("Class " + entityClass.getName() + " has multiple primary keys");
                information.setPrimaryKey(fieldData);
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

    private static @NotNull RepositoryInformation getInformation(
            @NotNull Class<?> entityClass,
            String tableName,
            Constraint[] constraints,
            Index[] indexes,
            Cacheable cacheable,
            Class<?>[] types,
            FetchPageSize fetchPageSize,
            Map<String, FieldData<?>> data,
            Map<String, FieldData<?>> oneToManyCache,
            Map<String, FieldData<?>> manyToOneCache,
            RepositoryAuditLogger repositoryAuditLogger,
            RepositoryEventLifecycleListener repositoryEventLifecycleListener,
            GlobalCacheable globalCacheable) {
        try {
            return new RepositoryInformation(
                    tableName, constraints, indexes, cacheable, entityClass, types,
                    fetchPageSize == null ? -1 : fetchPageSize.value(), data, oneToManyCache, manyToOneCache, globalCacheable, false,

                    repositoryAuditLogger == null ? null : repositoryAuditLogger.value().getDeclaredConstructor().newInstance(),
                    repositoryEventLifecycleListener == null ? null : repositoryEventLifecycleListener.value().getDeclaredConstructor().newInstance()
            );
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static <T> FieldData<T> createFieldData(RepositoryInformation information, @NotNull Field field, String tableName, String fieldName) {
        Named name = field.getAnnotation(Named.class);

        DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
        DefaultValueProvider defaultValueProvider = field.getAnnotation(DefaultValueProvider.class);

        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToMany != null || manyToOne != null || oneToOne != null) {
            information.setHasRelationships(true);
        }

        return new FieldData<>(
                information,
                name == null ? fieldName : name.value(),
                fieldName,
                tableName,
                FastField.create(field, true),
                field,
                (Class<T>) field.getType(),
                field.isAnnotationPresent(Id.class),
                field.isAnnotationPresent(AutoIncrement.class),
                field.isAnnotationPresent(NonNull.class),
                field.isAnnotationPresent(Unique.class),
                field.isAnnotationPresent(Now.class),
                field.getAnnotation(Constraint.class),
                field.getAnnotation(Condition.class),
                field.getAnnotation(OnUpdate.class),
                field.getAnnotation(OnDelete.class),
                oneToMany, manyToOne, oneToOne,
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> boolean isRepository(Class<T> elementType) {
        return getMetadata(elementType) != null;
    }
}
