package io.github.flameyossnowy.universal.microservices;

import io.github.flameyossnowy.universal.api.exceptions.json.JsonProcessException;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.uniform.json.JsonAdapter;
import io.github.flameyossnowy.uniform.json.dom.JsonObject;
import io.github.flameyossnowy.uniform.json.dom.JsonValue;
import io.github.flameyossnowy.uniform.json.exceptions.JsonException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public final class MicroservicesJsonCodecBridge {

    private MicroservicesJsonCodecBridge() {
        throw new AssertionError("No instances");
    }

    public static <T, ID> @NotNull JsonObject toStorageJson(
        @NotNull JsonAdapter objectMapper,
        @NotNull TypeResolverRegistry resolverRegistry,
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull T entity
    ) {
        JsonObject obj = objectMapper.valueToTree(entity);

        for (FieldModel<T> field : repositoryModel.fields()) {
            if (!field.isJson()) continue;

            @Nullable JsonValue fieldNode = obj.getRaw(field.name());
            if (fieldNode == null || fieldNode.isNull()) continue;

            Object typedValue = objectMapper.treeToValue(fieldNode, field.type());
            JsonCodec<Object> codec = resolverRegistry.getJsonCodec(field.jsonCodec());

            String json = codec.serialize(typedValue, (Class<Object>) field.type());
            try {
                obj.put(field.name(), objectMapper.readValue(json));
            } catch (JsonException e) {
                throw new JsonProcessException("Failed to serialize json field '" + field.name() + "'", e);
            }
        }

        return obj;
    }

    public static <T, ID> @NotNull JsonObject fromStorageJson(
        @NotNull JsonAdapter objectMapper,
        @NotNull TypeResolverRegistry resolverRegistry,
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull JsonValue value
    ) {
        if (!(value instanceof JsonObject obj)) {
            throw new JsonProcessException("Expected JSON object response for getAll");
        }

        for (FieldModel<T> field : repositoryModel.fields()) {
            if (!field.isJson()) continue;

            JsonValue fieldNode = obj.getRaw(field.name());
            if (fieldNode == null || fieldNode.isNull()) continue;

            try {
                String json = objectMapper.writeValue(fieldNode);
                JsonCodec<Object> codec = resolverRegistry.getJsonCodec(field.jsonCodec());
                Object typedValue = codec.deserialize(json, (Class<Object>) field.type());
                obj.put(field.name(), objectMapper.valueToTree(typedValue));
            } catch (JsonException e) {
                throw new RuntimeException("Failed to deserialize json field '" + field.name() + "'", e);
            }
        }

        return obj;
    }

    public static <T, ID> @Nullable T readEntityFromStorageJson(
        @NotNull JsonAdapter objectMapper,
        @NotNull TypeResolverRegistry resolverRegistry,
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull Class<T> entityType,
        @NotNull JsonValue storedNode
    ) {
        JsonObject converted = fromStorageJson(objectMapper, resolverRegistry, repositoryModel, storedNode);
        return objectMapper.treeToValue(converted, entityType);
    }
}
