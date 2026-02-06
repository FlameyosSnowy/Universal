package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;

import java.util.Collection;
import java.util.Map;

/**
 * Database-agnostic interface for handling collection, map, and array fields
 * in repository entities. Each database adapter (SQL, MongoDB, etc.) provides
 * its own implementation of this interface.
 */
public interface CollectionHandler {

    // ==================== Collection Operations ====================

    /**
     * Insert a collection of elements associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the collection field
     * @param values The collection to insert
     * @param elementType The type of elements in the collection
     * @param repoInfo Repository metadata
     */
    <T, ID> void insertCollection(
        ID parentId,
        String fieldName,
        Collection<?> values,
        Class<?> elementType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Fetch a collection associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the collection field
     * @param elementType The type of elements in the collection
     * @param kind The kind of collection to create (List, Set, etc.)
     * @param repoInfo Repository metadata
     * @return The fetched collection
     */
    <T, ID, C extends Collection<T>> C fetchCollection(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        CollectionKind kind,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Delete a specific element from a collection.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the collection field
     * @param element The element to delete
     * @param elementType The type of elements in the collection
     * @param repoInfo Repository metadata
     */
    <T, ID> void deleteFromCollection(
        ID parentId,
        String fieldName,
        T element,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Delete all elements from a collection.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the collection field
     * @param elementType The type of elements in the collection
     * @param repoInfo Repository metadata
     */
    <T, ID> void deleteAllFromCollection(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    );

    // ==================== Array Operations ====================

    /**
     * Insert an array associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the array field
     * @param arrayValue The array to insert
     * @param elementType The type of elements in the array
     * @param repoInfo Repository metadata
     */
    <T, ID> void insertArray(
        ID parentId,
        String fieldName,
        Object arrayValue,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Fetch an array associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the array field
     * @param elementType The type of elements in the array
     * @param repoInfo Repository metadata
     * @return The fetched array
     */
    <T, ID> T[] fetchArray(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Delete all elements from an array.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the array field
     * @param elementType The type of elements in the array
     * @param repoInfo Repository metadata
     */
    <T, ID> void deleteArray(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    );

    // ==================== Map Operations ====================

    /**
     * Insert a map associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the map field
     * @param values The map to insert
     * @param keyType The type of keys in the map
     * @param valueType The type of values in the map
     * @param repoInfo Repository metadata
     */
    <K, V, ID> void insertMap(
        ID parentId,
        String fieldName,
        Map<K, V> values,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Fetch a map associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the map field
     * @param keyType The type of keys in the map
     * @param valueType The type of values in the map
     * @param repoInfo Repository metadata
     * @return The fetched map
     */
    <K, V, ID> Map<K, V> fetchMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Insert a single key-value pair into a map.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the map field
     * @param key The key to insert
     * @param value The value to insert
     * @param keyType The type of keys in the map
     * @param valueType The type of values in the map
     * @param repoInfo Repository metadata
     */
    <K, V, ID> void insertMapEntry(
        ID parentId,
        String fieldName,
        K key,
        V value,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Delete a specific key from a map.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the map field
     * @param key The key to delete
     * @param keyType The type of keys in the map
     * @param valueType The type of values in the map
     * @param repoInfo Repository metadata
     */
    <K, V, ID> void deleteFromMap(
        ID parentId,
        String fieldName,
        K key,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Delete all entries from a map.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the map field
     * @param keyType The type of keys in the map
     * @param valueType The type of values in the map
     * @param repoInfo Repository metadata
     */
    <K, V, ID> void deleteAllFromMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    );

    // ==================== MultiMap Operations ====================

    /**
     * Insert a multimap (map with collection values) associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the multimap field
     * @param values The multimap to insert
     * @param keyType The type of keys in the multimap
     * @param valueType The type of values in the collections
     * @param repoInfo Repository metadata
     */
    <K, V, ID> void insertMultiMap(
        ID parentId,
        String fieldName,
        Map<K, ? extends Collection<V>> values,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    );

    /**
     * Fetch a multimap associated with a parent entity.
     *
     * @param parentId The ID of the parent entity
     * @param fieldName The name of the multimap field
     * @param keyType The type of keys in the multimap
     * @param valueType The type of values in the collections
     * @param kind The kind of collection to create for values
     * @param repoInfo Repository metadata
     * @return The fetched multimap
     */
    <K, V, ID, C extends Collection<V>> Map<K, C> fetchMultiMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        CollectionKind kind,
        RepositoryModel<?, ID> repoInfo
    );
}