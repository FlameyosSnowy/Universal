package io.github.flameyossnowy.universal.api.json;

public interface JsonCodec<T> {

    String serialize(T value, Class<T> targetType);

    T deserialize(String json, Class<T> targetType);

    /**
     * Optional: compute a patch between old and new.
     */
    default JsonPatch diff(T oldValue, T newValue, Class<T> targetType) {
        return new JsonPatch.FullReplace(serialize(newValue, targetType));
    }
}
