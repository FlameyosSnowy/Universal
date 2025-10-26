package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import io.github.flameyossnowy.universal.api.exceptions.ConstructorThrewException;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;

import static io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter.ADAPTERS;

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
            Object value = field.getValue(entity);
            doc.put(field.name(), value);
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
                args[index] = doc.get(fieldName);
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
        }

        return entity;
    }

    private void loadManyToOne(FieldData<?> field, Object entity, ID id) {
        var adapter = ADAPTERS.get(field.type());
        if (adapter == null) return;

        Object result = adapter.first(Query.select().where(field.name(), id).build());
        if (result != null) field.setValue(entity, result);
    }

    private void loadOneToOne(@NotNull FieldData<?> field, Object entity, ID id) {
        var adapter = ADAPTERS.get(field.type());
        if (adapter == null) return;

        Bson filter = createOneToManyFilter(adapter.getRepositoryInformation(), id);
        Document document = adapter.getCollection().find(filter).first();
        Object child = instantiateChildOneToOne(document, adapter.getRepositoryInformation(), entity);
        field.setValue(entity, child);
    }

    private void loadOneToMany(FieldData<?> field, Object parentEntity, Object parentId) {
        Class<?> childType = field.oneToMany().mappedBy();
        RepositoryInformation childInfo = RepositoryMetadata.getMetadata(childType);
        Objects.requireNonNull(childInfo, "Child repository metadata not found for type: " + childType);

        var adapter = ADAPTERS.get(childType);
        if (adapter == null) return;

        Bson filter = createOneToManyFilter(childInfo, parentId);

        try (MongoCursor<Document> iterable = adapter.getCollection().find(filter).iterator()) {
            int available = iterable.available();
            List<Object> children = new ArrayList<>(available);

            while (iterable.hasNext()) {
                Document childDoc = iterable.next();
                Object child = instantiateChildManyToOne(childDoc, childInfo, parentEntity);
                children.add(child);
            }

            field.setValue(parentEntity, children);
        }
    }

    private static @NotNull Object instantiateChildManyToOne(Document doc, RepositoryInformation childInfo, Object parentEntity) {
        Object child = childInfo.newInstance();
        for (FieldData<?> field : childInfo.getFields()) {
            if (field.manyToOne() != null) {
                field.setValue(child, parentEntity);
            } else {
                field.setValue(child, doc.get(field.name()));
            }
        }
        return child;
    }

    private static @NotNull Object instantiateChildOneToOne(Document doc, RepositoryInformation childInfo, Object parentEntity) {
        Object child = childInfo.newInstance();
        for (FieldData<?> field : childInfo.getFields()) {
            if (field.oneToOne() != null) {
                field.setValue(child, parentEntity);
            } else {
                field.setValue(child, doc.get(field.name()));
            }
        }
        return child;
    }

    private @NotNull Bson createOneToManyFilter(RepositoryInformation childInfo, Object parentId) {
        return childInfo.getManyToOneCache().values().stream()
                .filter(field -> field.type() == repoInfo.getType())
                .findFirst()
                .map(field -> Filters.eq(field.name(), parentId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No matching many-to-one field found in " + childInfo.getType()));
    }
}