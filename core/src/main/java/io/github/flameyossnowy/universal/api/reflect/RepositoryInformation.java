package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;
import me.sunlan.fastreflection.FastConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RepositoryInformation {
    private final String tableName;
    private boolean hasRelationships;
    private FieldData<?> primaryKey;
    private final Constraint[] constraints;
    private final Index[] indexes;
    private final Cacheable cacheable;
    private final Class<?> entityClass;
    private final Class<?>[] types;

    private final int fetchPageSize;

    private final Map<String, FieldData<?>> fieldDataMap;
    private final Map<String, FieldData<?>> oneToManyFields;
    private final Map<String, FieldData<?>> manyToOneFields;

    private static final Map<Class<?>, FastConstructor<?>> CACHE = new ConcurrentHashMap<>();

    public RepositoryInformation(String tableName,
                                 Constraint[] constraints, Index[] indexes, Cacheable cacheable,
                                 Class<?> entityClass, Class<?>[] types, int fetchPageSize,
                                 Map<String, FieldData<?>> fieldDataMap,
                                 Map<String, FieldData<?>> oneToManyCache,
                                 Map<String, FieldData<?>> manyToOneCache,
                                 boolean hasRelationships) {
        this.entityClass = entityClass;
        this.constraints = constraints;
        this.cacheable = cacheable;
        this.tableName = tableName;
        this.indexes = indexes;
        this.types = types;
        this.fetchPageSize = fetchPageSize;

        this.oneToManyFields = oneToManyCache;
        this.manyToOneFields = manyToOneCache;

        this.fieldDataMap = Collections.unmodifiableMap(fieldDataMap);

        this.hasRelationships = hasRelationships;
    }

    public String getRepositoryName() {
        return tableName;
    }

    public FieldData<?> getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(FieldData<?> primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Constraint[] getConstraints() {
        return constraints;
    }

    public Index[] getIndexes() {
        return indexes;
    }

    public Cacheable getCacheable() {
        return cacheable;
    }

    public Class<?> getType() {
        return entityClass;
    }

    public Class<?>[] getTypes() {
        return types;
    }

    public Collection<FieldData<?>> getFields() {
        return fieldDataMap.values();
    }

    public boolean hasRelationships() {
        return hasRelationships;
    }

    public void setHasRelationships(boolean hasRelationships) {
        this.hasRelationships = hasRelationships;
    }

    public FieldData<?> getField(String name) {
        return fieldDataMap.get(name);
    }

    @Override
    public String toString() {
        String constraints = Arrays.toString(this.constraints);
        String indexes = Arrays.toString(this.indexes);

        return "RepositoryInformation{" +
                "tableName='" + tableName + '\'' +
                ", primaryKey=" + primaryKey +
                ", constraints=" + (constraints.equals("null") ? "[]" : constraints) +
                ", indexes=" + (indexes.equals("null") ? "[]" : indexes) +
                ", cacheable=" + cacheable +
                ", entityClass=" + entityClass +
                ", types=" + Arrays.toString(types) +
                ", fieldDataMap=" + fieldDataMap +
                '}';
    }

    public int getFetchPageSize() {
        return fetchPageSize;
    }

    public @NotNull Object newInstance() {
        try {
            FastConstructor<?> constructor = CACHE.computeIfAbsent(entityClass, key -> {
                try {
                    return FastConstructor.create(entityClass.getDeclaredConstructor(), true);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
            return constructor.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, FieldData<?>> getManyToOneCache() {
        return manyToOneFields;
    }

    public Map<String, FieldData<?>> getOneToManyCache() {
        return oneToManyFields;
    }
}
