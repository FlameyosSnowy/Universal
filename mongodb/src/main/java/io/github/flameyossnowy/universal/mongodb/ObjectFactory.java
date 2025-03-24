package io.github.flameyossnowy.universal.mongodb;

import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import org.bson.Document;


import static io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter.ADAPTERS;

public class ObjectFactory {
    private final RepositoryInformation repositoryInformation;

    public ObjectFactory(RepositoryInformation repositoryInformation) {
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
        for (FieldData<?> field : repositoryInformation.getFields()) {
            Object value = document.get(field.name());
            if (field.manyToOne() != null) {
                var adapter = ADAPTERS.get(field.type());
                field.setValue(object, adapter.find(Query.select().where(field.name(), value).build()).list().get(0));
                continue;
            }
            if (field.oneToMany() != null) {
                var adapter = ADAPTERS.get(field.oneToMany().mappedBy());
                field.setValue(object, adapter.find(Query.select().where(field.name(), value).build()).list().get(0));
                continue;
            }
            field.setValue(object, value);
        }
        return object;
    }
}
