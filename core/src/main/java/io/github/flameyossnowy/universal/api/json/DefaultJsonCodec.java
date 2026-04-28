package io.github.flameyossnowy.universal.api.json;

import io.github.flameyossnowy.universal.api.exceptions.json.JsonProcessException;
import io.github.flameyossnowy.uniform.json.JsonAdapter;

/**
 * Default JSON codec that uses uniform-json for serialization/deserialization.
 */
public class DefaultJsonCodec<T> implements JsonCodec<T> {
    private final JsonAdapter mapper;

    public DefaultJsonCodec(JsonAdapter mapper) {
        this.mapper = mapper;
    }

    public DefaultJsonCodec() {
        this.mapper = new JsonAdapter(JsonAdapter.configBuilder().build());
    }

    @Override
    public String serialize(T value, Class<T> targetType) {
        if (value == null) {
            return "null";
        }
        try {
            return mapper.writeValue(value);
        } catch (Exception e) {
            throw new JsonProcessException("Failed to serialize: " + e.getMessage(), e);
        }
    }

    @Override
    public T deserialize(String json, Class<T> targetType) {
        if (json == null || json.equals("null")) {
            return null;
        }
        try {
            return mapper.readValue(json, targetType);
        } catch (Exception e) {
            throw new JsonProcessException("Failed to deserialize: " + e.getMessage(), e);
        }
    }
}
