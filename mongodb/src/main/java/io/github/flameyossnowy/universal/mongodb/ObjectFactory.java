package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;


import java.util.Objects;

import static io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter.ADAPTERS;

@SuppressWarnings("unchecked")
public class ObjectFactory<T, ID> {
    private final RepositoryInformation repositoryInformation;

    public ObjectFactory(RepositoryInformation repositoryInformation, Class<T> ignored, Class<ID> ignored2) {
        this.repositoryInformation = repositoryInformation;
    }

    public Document toDocument(Object object) {
        Document document = new Document();
        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.manyToOne() != null) continue;
            if (field.oneToMany() != null) continue;
            document.put(field.name(), field.getValue(object));
        }
        return document;
    }

    public Object fromDocument(Document document) {
        Object object = repositoryInformation.newInstance();

        ID id = null;
        for (FieldData<?> field : repositoryInformation.getFields()) {
            Object value = document.get(field.name());
            if (field.primary()) {
                id = (ID) value;
                field.setValue(object, value);
                continue;
            }
            if (field.manyToOne() != null) {
                var adapter = ADAPTERS.get(field.type());
                field.setValue(object, adapter.find(Query.select().where(field.name(), id).build()).list().get(0));
                continue;
            }
            if (field.oneToMany() != null) {
                Class<?> childType = field.oneToMany().mappedBy();

                RepositoryInformation childInformation = RepositoryMetadata.getMetadata(childType);
                Objects.requireNonNull(childInformation);

                var adapter = ADAPTERS.get(childType);

                Bson filter = getFilter(childInformation, value);
                FindIterable<Document> findIterable = adapter.collection.find(filter);
                for (Document doc : findIterable) {
                    Object child = createInstance(doc, childInformation, object);
                    field.setValue(object, child);
                }
                continue;
            }
            field.setValue(object, value);
        }
        return object;
    }

    private static @NotNull Object createInstance(Document doc, RepositoryInformation childInformation, Object object) {
        Object child = childInformation.newInstance();
        for (FieldData<?> relatedField : childInformation.getFields()) {
            if (relatedField.manyToOne() != null) relatedField.setValue(child, object);
            relatedField.setValue(child, doc.get(relatedField.name()));
        }
        return child;
    }

    private @NotNull Bson getFilter(RepositoryInformation childInformation, Object value) {
        for (FieldData<?> childField : childInformation.getManyToOneCache().values()) {
            if (childField.type() != repositoryInformation.getType()) continue;
            return Filters.eq(childField.name(), value);
        }

        throw new IllegalArgumentException("Could not find filter for " + childInformation.getType());
    }
}
