package io.github.flameyossnowy.universal.api.json;

import io.github.flameyossnowy.universal.api.exceptions.json.JsonProcessException;
import me.flame.uniform.json.JsonAdapter;
import me.flame.uniform.json.exceptions.JsonException;

public class DefaultJsonCodec<T> implements JsonCodec<T> {
    private final JsonAdapter mapper;

    public DefaultJsonCodec(JsonAdapter mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(T value, Class<T> targetType) {
        try {
            return mapper.writeValue(value);
        } catch (JsonException e) {
            throw new JsonProcessException(e.getMessage(), e);
        }
    }

    @Override
    public T deserialize(String json, Class<T> targetType) {
        try {
            return mapper.readValue(json, targetType);
        } catch (JsonProcessException e) {
            throw new JsonProcessException(e.getMessage(), e);
        }
    }
}
