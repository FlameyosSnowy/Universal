package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class GeneratedObjectFactories {
    private static final Map<String, ModelEntry<?, ?>> MODELS = new HashMap<>(16);
    private static final Map<String, RelationshipHandler<?, ?>> RELATIONSHIP_HANDLERS = new HashMap<>(16);

    public static final class ModelEntry<T, ID> {
        public final Class<T> elementType;
        public final Class<ID> idType;
        public final Function<RepositoryModel<T, ID>, ObjectModel<T, ID>> factory;

        ModelEntry(
            Class<T> elementType,
            Class<ID> idType,
            Function<RepositoryModel<T, ID>, ObjectModel<T, ID>> factory
        ) {
            this.elementType = elementType;
            this.idType = idType;
            this.factory = factory;
        }
    }

    public static <T, ID> void add(
        String modelName,
        Class<T> elementType,
        Class<ID> idType,
        Function<RepositoryModel<T, ID>, ObjectModel<T, ID>> model
    ) {
        MODELS.put(modelName, new ModelEntry<>(elementType, idType, model));
    }

    @SuppressWarnings("unchecked")
    public static <E, K> ObjectModel<E, K> getObjectModel(RepositoryModel<?, ?> repositoryModel) {
        ModelEntry<?, ?> raw = MODELS.get(repositoryModel.tableName());
        if (raw == null) {
            throw new IllegalArgumentException("Unknown model: " + repositoryModel.tableName());
        }

        if (raw.elementType != repositoryModel.getEntityClass()
            || raw.idType != repositoryModel.getIdClass()) {
            throw new IllegalStateException("Type mismatch for model: " + repositoryModel.tableName());
        }

        ModelEntry<E, K> entry = (ModelEntry<E, K>) raw;
        return entry.factory.apply((RepositoryModel<E, K>) repositoryModel);
    }

    @SuppressWarnings("unchecked")
    public static <E, K> RelationshipHandler<E, K> getRelationshipHandler(String entityQualifiedName) {
        return (RelationshipHandler<E, K>) RELATIONSHIP_HANDLERS.get(entityQualifiedName);
    }

    private GeneratedObjectFactories() {}
}