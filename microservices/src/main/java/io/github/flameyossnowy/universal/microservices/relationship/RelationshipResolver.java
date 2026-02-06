package io.github.flameyossnowy.universal.microservices.relationship;

import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RelationshipModel;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Resolves relationships for entities by delegating to a RelationshipHandler.
 * This class populates relationship fields on entities after they've been loaded.
 */
public record RelationshipResolver<T, ID>(RelationshipHandler<T, ID> handler) {
    /**
     * Resolves all relationship fields for the given entity.
     *
     * @param entity                The entity to populate relationships for
     * @param repositoryInformation Metadata about the entity's repository
     */
    public void resolve(T entity, @NotNull RepositoryModel<T, ID> repositoryInformation) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        Objects.requireNonNull(repositoryInformation, "Repository information cannot be null");

        ID id = repositoryInformation.getPrimaryKeyValue(entity);
        if (id == null) {
            throw new IllegalStateException(
                "Cannot resolve relationships for entity without primary key: " +
                    repositoryInformation.getEntityClass().getSimpleName()
            );
        }

        // Iterate through all relationship fields
        for (RelationshipModel<T, ID> rel : repositoryInformation.getRelationships()) {
            resolveRelationship(entity, rel, id);
        }
    }

    /**
     * Resolves a single relationship field.
     */
    private void resolveRelationship(T entity, RelationshipModel<T, ID> rel, ID id) {
        RelationshipKind kind = rel.relationshipKind();
        FieldModel<T> field = rel.getFieldModel();

        Object result = switch (kind) {
            case ONE_TO_MANY -> handler.handleOneToManyRelationship(id, field);
            case MANY_TO_ONE -> handler.handleManyToOneRelationship(id, field);
            case ONE_TO_ONE -> handler.handleOneToOneRelationship(id, field);
        };

        // Set the resolved value on the entity
        if (result != null) {
            rel.set(entity, result);
        }
    }

    /**
     * Resolves only specific relationship fields for the given entity.
     * Useful for selective eager loading.
     *
     * @param entity                The entity to populate relationships for
     * @param repositoryInformation Metadata about the entity's repository
     * @param fieldNames            The names of relationship fields to resolve
     */
    public void resolveFields(
        T entity,
        @NotNull RepositoryModel<T, ID> repositoryInformation,
        @NotNull String... fieldNames
    ) {
        Objects.requireNonNull(entity, "Entity cannot be null");
        Objects.requireNonNull(repositoryInformation, "Repository information cannot be null");
        Objects.requireNonNull(fieldNames, "Field names cannot be null");

        ID id = repositoryInformation.getPrimaryKeyValue(entity);
        if (id == null) {
            throw new IllegalStateException(
                "Cannot resolve relationships for entity without primary key: " +
                    repositoryInformation.getEntityClass().getSimpleName()
            );
        }

        for (String fieldName : fieldNames) {
            RelationshipModel<T, ID> rel = null;
            for (RelationshipModel<T, ID> r : repositoryInformation.getRelationships()) {
                if (r.fieldName().equals(fieldName)) {
                    rel = r;
                    break;
                }
            }

            if (rel == null) {
                throw new IllegalArgumentException(
                    "Field not found: " + fieldName + " in " +
                        repositoryInformation.getEntityClass().getSimpleName()
                );
            }

            resolveRelationship(entity, rel, id);
        }
    }

    /**
     * Resolves relationships for multiple entities at once.
     * This may be more efficient than resolving each entity individually
     * as the handler can potentially batch operations.
     *
     * @param entities              The entities to populate relationships for
     * @param repositoryInformation Metadata about the entity's repository
     */
    public void resolveAll(
        @NotNull Iterable<T> entities,
        @NotNull RepositoryModel<T, ID> repositoryInformation
    ) {
        Objects.requireNonNull(entities, "Entities cannot be null");
        Objects.requireNonNull(repositoryInformation, "Repository information cannot be null");

        for (T entity : entities) {
            if (entity != null) {
                resolve(entity, repositoryInformation);
            }
        }
    }
}