package io.github.flameyossnowy.universal.microservices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unchecked")
public final class MicroservicesJsonCodecBridge {

    private MicroservicesJsonCodecBridge() {
        throw new AssertionError("No instances");
    }

    public static <T, ID> @NotNull JsonNode toStorageJson(
        @NotNull ObjectMapper objectMapper,
        @NotNull TypeResolverRegistry resolverRegistry,
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull T entity
    ) {
        JsonNode node = objectMapper.valueToTree(entity);
        if (!(node instanceof ObjectNode obj)) {
            return node;
        }

        for (FieldModel<T> field : repositoryModel.fields()) {
            if (!field.isJson()) continue;

            JsonNode fieldNode = obj.get(field.name());
            if (fieldNode == null || fieldNode.isNull()) continue;

            Object typedValue = objectMapper.convertValue(fieldNode, field.type());
            JsonCodec<Object> codec = resolverRegistry.getJsonCodec(field.jsonCodec());

            String json = codec.serialize(typedValue, (Class<Object>) field.type());
            try {
                obj.set(field.name(), objectMapper.readTree(json));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize json field '" + field.name() + "'", e);
            }
        }

        return obj;
    }

    public static <T, ID> @NotNull JsonNode fromStorageJson(
        @NotNull ObjectMapper objectMapper,
        @NotNull TypeResolverRegistry resolverRegistry,
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull JsonNode storedNode
    ) {
        if (!(storedNode instanceof ObjectNode obj)) {
            return storedNode;
        }

        for (FieldModel<T> field : repositoryModel.fields()) {
            if (!field.isJson()) continue;

            JsonNode fieldNode = obj.get(field.name());
            if (fieldNode == null || fieldNode.isNull()) continue;

            try {
                String json = objectMapper.writeValueAsString(fieldNode);
                JsonCodec<Object> codec = resolverRegistry.getJsonCodec(field.jsonCodec());
                Object typedValue = codec.deserialize(json, (Class<Object>) field.type());
                obj.set(field.name(), objectMapper.valueToTree(typedValue));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize json field '" + field.name() + "'", e);
            }
        }

        return obj;
    }

    public static <T, ID> @NotNull T readEntityFromStorageJson(
        @NotNull ObjectMapper objectMapper,
        @NotNull TypeResolverRegistry resolverRegistry,
        @NotNull RepositoryModel<T, ID> repositoryModel,
        @NotNull Class<T> entityType,
        @NotNull JsonNode storedNode
    ) {
        JsonNode converted = fromStorageJson(objectMapper, resolverRegistry, repositoryModel, storedNode);
        return objectMapper.convertValue(converted, entityType);
    }
}
