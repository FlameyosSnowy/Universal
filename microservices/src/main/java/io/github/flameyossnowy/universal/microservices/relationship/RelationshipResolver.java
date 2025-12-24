package io.github.flameyossnowy.universal.microservices.relationship;

import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class RelationshipResolver<ID> {
    private final RelationshipHandler<ID> handler;

    public RelationshipResolver(RelationshipHandler<ID> handler) {
        this.handler = handler;
    }

    public <T> void resolve(T entity, @NotNull RepositoryInformation repositoryInformation) {
        Objects.requireNonNull(entity);

        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        Objects.requireNonNull(primaryKey);

        ID id = primaryKey.getValue(entity);

        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.oneToMany() != null) {
                List<Object> result = handler.handleOneToManyRelationship(field, id);
                field.setValue(entity, result);
            } else if (field.manyToOne() != null) {
                Object result = handler.handleManyToOneRelationship(id, field);
                field.setValue(entity, result);
            } else if (field.oneToOne() != null) {
                Object result = handler.handleOneToOneRelationship(id, field);
                field.setValue(entity, result);
            }
        }
    }
}