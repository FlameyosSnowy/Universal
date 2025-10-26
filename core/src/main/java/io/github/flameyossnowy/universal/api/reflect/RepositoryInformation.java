package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.exceptions.ConstructorThrewException;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;

import me.sunlan.fastreflection.FastConstructor;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RepositoryInformation {
    private final String tableName;
    private boolean hasRelationships;
    private final List<FieldData<?>> primaryKeys = new ArrayList<>();
    private final Constraint[] constraints;
    private final Index[] indexes;
    private final Cacheable cacheable;
    private final Class<?> entityClass;
    private final AuditLogger<?> auditLogger;
    private final EntityLifecycleListener<?> entityLifecycleListener;
    private final ExceptionHandler<?, ?, ?> exceptionHandler;

    private final int fetchPageSize;

    private final Map<String, FieldData<?>> fieldDataMap;
    private final Map<String, FieldData<?>> oneToManyFields;
    private final Map<String, FieldData<?>> manyToOneFields;
    private final Map<String, FieldData<?>> oneToOneFields;

    private static final Map<Class<?>, FastConstructor<?>> CACHE = new ConcurrentHashMap<>(5);
    private final GlobalCacheable globalCacheable;

    private final boolean isRecord;
    private final Constructor<?> recordConstructor;
    private final RecordComponent[] recordComponents;
    public RepositoryInformation(String tableName,
                                 Constraint[] constraints, Index[] indexes, Cacheable cacheable,
                                 Class<?> entityClass, int fetchPageSize,
                                 Map<String, FieldData<?>> fieldDataMap,
                                 Map<String, FieldData<?>> oneToManyCache,
                                 Map<String, FieldData<?>> manyToOneCache,
                                 Map<String, FieldData<?>> oneToOneCache,
                                 AuditLogger<?> auditLogger,
                                 EntityLifecycleListener<?> entityLifecycleListener,
                                 ExceptionHandler<?, ?, ?> exceptionHandler,
                                 GlobalCacheable globalCacheable) {
        this.constraints = constraints;
        this.entityClass = entityClass;
        this.globalCacheable = globalCacheable;
        this.cacheable = cacheable;
        this.tableName = tableName;
        this.indexes = indexes;
        this.fetchPageSize = fetchPageSize;

        this.oneToManyFields = oneToManyCache;
        this.manyToOneFields = manyToOneCache;
        this.oneToOneFields = oneToOneCache;

        this.fieldDataMap = Collections.unmodifiableMap(fieldDataMap);

        this.auditLogger = auditLogger;
        this.entityLifecycleListener = entityLifecycleListener;
        this.exceptionHandler = exceptionHandler;

        this.isRecord = entityClass.isRecord();
        
        List<FieldData<?>> keys = fieldDataMap.values().stream()
                .filter(FieldData::primary)
                .sorted(Comparator.comparing(FieldData::name))
                .toList();
        
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("No primary key found for entity " + entityClass.getSimpleName());
        }
        
        this.primaryKeys.addAll(keys);
        
        if (isRecord) {
            try {
                this.recordComponents = entityClass.getRecordComponents();
                this.recordConstructor = entityClass.getDeclaredConstructor(
                        Arrays.stream(recordComponents).map(RecordComponent::getType).toArray(Class[]::new)
                );
            } catch (Exception e) {
                throw new RepositoryException("Failed to cache record constructor.", e);
            }
        } else {
            this.recordComponents = null;
            this.recordConstructor = null;
        }
    }

    public String getRepositoryName() {
        return tableName;
    }

    public FieldData<?> getPrimaryKey() {
        if (primaryKeys.isEmpty()) {
            return null;
        }
        return primaryKeys.get(0);
    }
    
    public List<FieldData<?>> getPrimaryKeys() {
        return Collections.unmodifiableList(primaryKeys);
    }
    
    public boolean hasCompositeKey() {
        return primaryKeys.size() > 1;
    }

    // Primary key is set during construction and cannot be modified

    public Constraint[] getConstraints() {
        return constraints;
    }

    public Index[] getIndexes() {
        return indexes;
    }

    public Cacheable getCacheable() {
        return cacheable;
    }

    public GlobalCacheable getGlobalCacheable() {
        return globalCacheable;
    }

    public Map<String, FieldData<?>> getOneToOneCache() {
        return oneToOneFields;
    }

    public Class<?> getType() {
        return entityClass;
    }

    public boolean isRecord() {
        return isRecord;
    }

    public Constructor<?> getRecordConstructor() {
        return recordConstructor;
    }


    public RecordComponent[] getRecordComponents() {
        return recordComponents;
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
                ", primaryKeys=" + primaryKeys +
                ", constraints=" + (constraints.equals("null") ? "[]" : constraints) +
                ", indexes=" + (indexes.equals("null") ? "[]" : indexes) +
                ", cacheable=" + cacheable +
                ", entityClass=" + entityClass +
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
                    return FastConstructor.create(entityClass.getDeclaredConstructor());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
            return constructor.invoke();
        } catch (InvocationTargetException e) {
            throw new ConstructorThrewException(e.getMessage());
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

    public AuditLogger<?> getAuditLogger() {
        return auditLogger;
    }

    public EntityLifecycleListener<?> getEntityLifecycleListener() {
        return entityLifecycleListener;
    }

    public ExceptionHandler<?, ?, ?> getExceptionHandler() {
        return exceptionHandler;
    }

    public void addPrimaryKey(FieldData<?> fieldData) {
        primaryKeys.add(fieldData);
    }
}
