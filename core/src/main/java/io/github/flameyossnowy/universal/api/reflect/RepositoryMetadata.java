package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RepositoryMetadata {
    private static final Map<Class<?>, RepositoryInformation> cache = new HashMap<>();

    public static RepositoryInformation getMetadata(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, RepositoryMetadata::buildMetadata);
    }

    private static RepositoryInformation buildMetadata(@NotNull Class<?> entityClass) {
        Repository repositoryAnnotation = entityClass.getAnnotation(Repository.class);
        if (repositoryAnnotation == null) return null;

        if (entityClass.getSuperclass().isAnnotationPresent(Repository.class)) {
            throw new IllegalArgumentException("Class " + entityClass.getName() + " is annotated with @Repository and has a superclass also annotated with @Repository");
        }

        String tableName = repositoryAnnotation.name();
        Constraint[] constraints = entityClass.getAnnotationsByType(Constraint.class);
        Index[] indexes = entityClass.getAnnotationsByType(Index.class);
        Cacheable cacheable = entityClass.getAnnotation(Cacheable.class);

        FieldDataRegistry registry = new FieldDataRegistry(entityClass);
        registry.processFields(tableName);

        return new RepositoryInformation(
                tableName, registry.getPrimaryKey(), constraints, indexes, cacheable, entityClass,
                registry.getTypes(),
                registry.getFieldDataMap(),
                registry.getFieldDataMap().values()
        );
    }

}
