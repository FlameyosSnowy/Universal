package io.github.flameyossnowy.universal.api.repository;

import io.github.flameyossnowy.universal.api.annotations.*;
import me.sunlan.fastreflection.FastField;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;

public class RepositoryMetadata {
    private static final Map<Class<?>, RepositoryInformation> cache = new HashMap<>();

    public static RepositoryInformation getMetadata(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, RepositoryMetadata::buildMetadata);
    }

    private static @NotNull RepositoryInformation buildMetadata(@NotNull Class<?> entityClass) {
        Repository entityAnnotation = entityClass.getAnnotation(Repository.class);
        if (entityAnnotation == null)
            throw new IllegalArgumentException("Class " + entityClass.getName() + " is not annotated with @Repository/@Destruct");

        if (entityClass.getSuperclass().isAnnotationPresent(Repository.class))
            throw new IllegalArgumentException("Class " + entityClass.getName() + " is annotated with @Repository and has a superclass annotated with @Repository");

        Constraint[] constraints = entityClass.getAnnotationsByType(Constraint.class);

        String name = entityAnnotation.name();

        Field[] fields = entityClass.getDeclaredFields();
        Map<String, FieldData<?>> fieldData = new LinkedHashMap<>();
        Class<?>[] types = new Class<?>[fields.length];

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);
            Class<?> fieldType = field.getType();

            fieldData.put(field.getName(), new FieldData<>(
                    field.getName(),
                    name,
                    FastField.create(field, true),
                    field,
                    fieldType, // Now correctly typed
                    field.isAnnotationPresent(Id.class),
                    field.isAnnotationPresent(AutoIncrement.class),
                    field.isAnnotationPresent(NonNull.class),
                    field.isAnnotationPresent(Unique.class),
                    field.getAnnotation(Constraint.class),
                    field.getAnnotation(References.class),
                    field.getAnnotation(Condition.class),
                    field.getAnnotation(OnUpdate.class),
                    field.getAnnotation(OnDelete.class),
                    field.getAnnotation(Foreign.class),
                    field.getAnnotation(Resolver.class)
            ));
            types[i] = fieldType;
        }

        return new RepositoryInformation(name, constraints, entityClass, types, fieldData, fieldData.values());
    }

    public record RepositoryInformation(String repository, Constraint[] constraints, Class<?> type, Class<?>[] types, Map<String, FieldData<?>> fieldData, Collection<FieldData<?>> fields) {}

    public record FieldData<T>(String name,
                            String tableName,
                            FastField field,
                            Field rawField,
                            Class<T> type,
                            boolean primary,
                            boolean autoIncrement,
                            boolean nonNull,
                            boolean unique,
                            Constraint constraint,
                            References references,
                            Condition condition,
                            OnUpdate onUpdate,
                            OnDelete onDelete,
                            Foreign foreign,
                            Resolver resolver) {}
}
