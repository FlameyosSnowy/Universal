package io.github.flameyossnowy.universal.microservices;

import io.github.flameyossnowy.uniform.json.JsonAdapter;
import io.github.flameyossnowy.uniform.json.dom.*;
import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;

import java.util.*;

/**
 * {@link CollectionHandler} for file-based JSON storage.
 *
 * <p>In the file adapter, collections, arrays, and maps are stored <em>inline</em>
 * inside the entity's JSON file — there are no join tables or separate documents.
 * This means:
 *
 * <ul>
 *   <li>All <strong>mutating</strong> operations (insert*, delete*) are
 *       <strong>no-ops</strong>. Persistence happens atomically when the whole
 *       entity file is (re)written by {@code FileEntityStore.write()}.</li>
 *   <li>All <strong>fetch</strong> operations read from the {@link JsonObject}
 *       that was parsed from the entity file and passed in at construction time,
 *       delegating type conversion to {@link JsonAdapter}.</li>
 * </ul>
 */
public final class JsonCollectionHandler implements CollectionHandler {

    private final JsonAdapter  objectMapper;
    private final JsonObject   entityNode;

    /**
     * @param objectMapper the Uniform adapter used for element-level type conversion
     * @param entityNode   the already-parsed JSON object for the current entity
     */
    public JsonCollectionHandler(JsonAdapter objectMapper, JsonObject entityNode) {
        this.objectMapper = objectMapper;
        this.entityNode   = entityNode;
    }

    // =========================================================================
    // Collection — fetch
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID, C extends Collection<T>> C fetchCollection(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        CollectionKind kind,
        RepositoryModel<?, ID> repoInfo
    ) {
        JsonValue raw = entityNode.getRaw(fieldName);
        if (raw == null || raw instanceof JsonNull) {
            return (C) kind.createEmpty();
        }

        if (!(raw instanceof JsonArray arr)) {
            throw new IllegalStateException(
                "Expected JSON array for field '" + fieldName + "' but got: " + raw.getClass().getSimpleName());
        }

        Collection<T> result = kind.createEmpty();
        for (JsonValue element : arr) {
            result.add(objectMapper.readValue(element, elementType));
        }
        return (C) result;
    }

    // =========================================================================
    // Array — fetch
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID> T[] fetchArray(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        JsonValue raw = entityNode.getRaw(fieldName);
        if (raw == null || raw instanceof JsonNull) {
            return (T[]) java.lang.reflect.Array.newInstance(elementType, 0);
        }

        if (!(raw instanceof JsonArray arr)) {
            throw new IllegalStateException(
                "Expected JSON array for field '" + fieldName + "' but got: " + raw.getClass().getSimpleName());
        }

        T[] result = (T[]) java.lang.reflect.Array.newInstance(elementType, arr.size());
        int i = 0;
        for (JsonValue element : arr) {
            result[i++] = objectMapper.readValue(element, elementType);
        }
        return result;
    }

    // =========================================================================
    // Map — fetch
    // =========================================================================

    @Override
    public <K, V, ID> Map<K, V> fetchMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        JsonValue raw = entityNode.getRaw(fieldName);
        if (raw == null || raw instanceof JsonNull) return new LinkedHashMap<>();

        if (!(raw instanceof JsonObject obj)) {
            throw new IllegalStateException(
                "Expected JSON object for map field '" + fieldName + "' but got: " + raw.getClass().getSimpleName());
        }

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : obj.entries()) {
            K key   = objectMapper.readValue(new io.github.flameyossnowy.uniform.json.dom.JsonString(entry.getKey()), keyType);
            V value = objectMapper.readValue(entry.getValue(), valueType);
            result.put(key, value);
        }
        return result;
    }

    // =========================================================================
    // MultiMap — fetch
    // =========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <K, V, ID, C extends Collection<V>> Map<K, C> fetchMultiMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        CollectionKind kind,
        RepositoryModel<?, ID> repoInfo
    ) {
        JsonValue raw = entityNode.getRaw(fieldName);
        if (raw == null || raw instanceof JsonNull) return new LinkedHashMap<>();

        if (!(raw instanceof JsonObject obj)) {
            throw new IllegalStateException(
                "Expected JSON object for multimap field '" + fieldName + "' but got: " + raw.getClass().getSimpleName());
        }

        Map<K, C> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : obj.entries()) {
            K key = objectMapper.readValue(new io.github.flameyossnowy.uniform.json.dom.JsonString(entry.getKey()), keyType);

            if (!(entry.getValue() instanceof JsonArray arr)) {
                throw new IllegalStateException(
                    "Expected JSON array for multimap values at key '" + entry.getKey() + "'");
            }

            Collection<V> values = kind.createEmpty();
            for (JsonValue element : arr) {
                values.add(objectMapper.readValue(element, valueType));
            }
            result.put(key, (C) values);
        }
        return result;
    }

    // =========================================================================
    // All mutating operations — no-ops
    //
    // File storage is document-oriented: the entire entity (including all inline
    // collections) is written atomically by FileEntityStore.write(). There is no
    // concept of inserting or deleting individual collection rows separately.
    // =========================================================================

    @Override
    public <T, ID> void insertCollection(ID parentId, String fieldName, java.util.Collection<?> values,
                                         Class<?> elementType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <T, ID> void deleteFromCollection(ID parentId, String fieldName, T element,
                                             Class<T> elementType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <T, ID> void deleteAllFromCollection(ID parentId, String fieldName,
                                                Class<T> elementType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <T, ID> void insertArray(ID parentId, String fieldName, Object arrayValue,
                                    Class<T> elementType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <T, ID> void deleteArray(ID parentId, String fieldName,
                                    Class<T> elementType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <K, V, ID> void insertMap(ID parentId, String fieldName, Map<K, V> values,
                                     Class<K> keyType, Class<V> valueType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <K, V, ID> void insertMapEntry(ID parentId, String fieldName, K key, V value,
                                          Class<K> keyType, Class<V> valueType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <K, V, ID> void deleteFromMap(ID parentId, String fieldName, K key,
                                         Class<K> keyType, Class<V> valueType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <K, V, ID> void deleteAllFromMap(ID parentId, String fieldName,
                                            Class<K> keyType, Class<V> valueType, RepositoryModel<?, ID> repoInfo) { }

    @Override
    public <K, V, ID> void insertMultiMap(ID parentId, String fieldName, Map<K, ? extends Collection<V>> values,
                                          Class<K> keyType, Class<V> valueType, RepositoryModel<?, ID> repoInfo) { }
}