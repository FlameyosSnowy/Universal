package io.github.flameyossnowy.universal.api.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flameyossnowy.universal.api.exceptions.json.JacksonJsonLocation;
import io.github.flameyossnowy.universal.api.exceptions.json.JsonProcessException;

public class DefaultJsonCodec<T> implements JsonCodec<T> {
    private final ObjectMapper mapper;

    public DefaultJsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(T value, Class<T> targetType) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonProcessException(e.getMessage(), e, JacksonJsonLocation.from(e.getLocation()));
        }
    }

    @Override
    public T deserialize(String json, Class<T> targetType) {
        try {
            return mapper.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            throw new JsonProcessException(e.getMessage(), e, JacksonJsonLocation.from(e.getLocation()));
        }
    }
}
