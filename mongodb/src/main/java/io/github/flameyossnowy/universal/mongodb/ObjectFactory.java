package io.github.flameyossnowy.universal.mongodb;

import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.exceptions.ConstructorThrewException;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodec;
import org.bson.Document;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public class ObjectFactory<T, ID> {

    private final RepositoryInformation repoInfo;
    private final boolean isRecord;
    private final RecordComponent[] recordComponents;

    @SuppressWarnings("unused")
    public ObjectFactory(@NotNull RepositoryInformation repoInfo, Class<T> type, Class<ID> idClass) {
        this.repoInfo = repoInfo;
        this.isRecord = repoInfo.isRecord();
        this.recordComponents = isRecord ? repoInfo.getRecordComponents() : null;

        if (isRecord) {
            for (FieldData<?> field : repoInfo.getFields()) {
                if (field.oneToOne() != null || field.oneToMany() != null || field.manyToOne() != null) {
                    throw new UnsupportedOperationException("Records cannot contain relationships like oneToOne, oneToMany, or manyToOne: " + field.name());
                }
            }
        }
    }

    public Document toDocument(Object entity) {
        Document doc = new Document();

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.manyToOne() != null || field.oneToMany() != null || field.oneToOne() != null) continue;
            
            // Set field context for codec to use
            MongoTypeCodec.setCurrentFieldName(field.name());
            try {
                Object value = field.getValue(entity);
                doc.put(field.name(), value);
            } finally {
                // Always clear the ThreadLocal to prevent leaks
                MongoTypeCodec.setCurrentFieldName(null);
            }
        }

        return doc;
    }

    public T fromDocument(Document doc) {
        if (isRecord) {
            int length = recordComponents.length;
            Object[] args = new Object[length];
            for (int index = 0; index < length; index++) {
                RecordComponent rc = recordComponents[index];
                String fieldName = rc.getName();
                
                // Set field context for codec to use during deserialization
                io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodec.setCurrentFieldName(fieldName);
                try {
                    args[index] = doc.get(fieldName);
                } finally {
                    io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodec.setCurrentFieldName(null);
                }
            }
            try {
                return (T) repoInfo.getRecordConstructor().newInstance(args);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new ConstructorThrewException(e.getMessage());
            }
        }

        T entity = (T) repoInfo.newInstance();
        ID entityId = null;

        for (FieldData<?> field : repoInfo.getFields()) {
            String fieldName = field.name();
            
            // Set field context for codec to use during deserialization
            io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodec.setCurrentFieldName(fieldName);
            try {
                Object value = doc.get(fieldName);

                if (field.primary()) {
                    entityId = (ID) value;
                    field.setValue(entity, value);
                    continue;
                }

                if (field.manyToOne() != null) {
                    loadManyToOne(field, entity, entityId);
                    continue;
                }

                if (field.oneToOne() != null) {
                    loadOneToOne(field, entity, entityId);
                    continue;
                }

                if (field.oneToMany() != null) {
                    loadOneToMany(field, entity, value);
                    continue;
                }

                field.setValue(entity, value);
            } finally {
                io.github.flameyossnowy.universal.mongodb.codec.MongoTypeCodec.setCurrentFieldName(null);
            }
        }

        return entity;
    }

    private void loadManyToOne(FieldData<?> field, Object entity, ID id) {
        var adapter = RepositoryRegistry.get(field.type());
        if (adapter == null) return;

        Object result = adapter.first(Query.select().where(field.name(), id).build());
        if (result != null) field.setValue(entity, result);
    }

    private void loadOneToOne(@NotNull FieldData<?> field, @NotNull Object entity, ID id) {
        RepositoryInformation metadata = RepositoryMetadata.getMetadata(entity.getClass());
        var adapter = RepositoryRegistry.get(field.type());
        if (adapter == null) return;

        RepositoryInformation repositoryInformation = adapter.getRepositoryInformation();
        SelectQuery filter = createOneToManyFilter(repositoryInformation, id);
        Object document = adapter.first(filter);
        Object child = instantiateChildOneToOne(document, repositoryInformation, entity, metadata);
        field.setValue(entity, child);
    }

    private void loadOneToMany(@NotNull FieldData<?> field, Object parentEntity, Object parentId) {
        Class<?> childType = field.oneToMany().mappedBy();
        RepositoryInformation childInfo = RepositoryMetadata.getMetadata(childType);
        Objects.requireNonNull(childInfo, "Child repository metadata not found for type: " + childType);

        var adapter = RepositoryRegistry.get(childType);
        if (adapter == null) return;

        SelectQuery filter = createOneToManyFilter(childInfo, parentId);
        List<Object> parentList = (List<Object>) adapter.find(filter);
        List<Object> children = new ArrayList<>(parentList.size());

        for (Object childObject : parentList) {
            Object child = instantiateChildManyToOne(childObject, childInfo, parentEntity, childInfo);
            children.add(child);
        }

        field.setValue(parentEntity, children);
    }

    private static @NotNull Object instantiateChildManyToOne(
            Object doc, @NotNull RepositoryInformation childInfo,
            Object parentEntity, RepositoryInformation info
    ) {
        Object child = childInfo.newInstance();
        for (FieldData<?> field : childInfo.getFields()) {
            if (field.manyToOne() != null) {
                field.setValue(child, parentEntity);
            } else {
                field.setValue(child, info.getField(field.name()).getValue(doc));
            }
        }
        return child;
    }

    private static @NotNull Object instantiateChildOneToOne(
            Object doc, RepositoryInformation childInfo,
            Object parentEntity, RepositoryInformation info
    ) {
        Object child = childInfo.newInstance();
        for (FieldData<?> field : childInfo.getFields()) {
            if (field.oneToOne() != null) {
                field.setValue(child, parentEntity);
            } else {
                field.setValue(child, info.getField(field.name()).getValue(doc));
            }
        }
        return child;
    }

    private @NotNull SelectQuery createOneToManyFilter(RepositoryInformation childInfo, Object parentId) {
        return childInfo.getManyToOneCache().values().stream()
                .filter(field -> field.type() == repoInfo.getType())
                .findFirst()
                .map(field -> Query.select().where(field.name(), parentId).build())
                .orElseThrow(() -> new IllegalArgumentException("No matching many-to-one field found in " + childInfo.getType()));
    }
}