package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.lang.reflect.Array;
import java.util.*;

/**
 * MongoDB implementation of CollectionHandler.
 * Stores collections, maps, and arrays as embedded documents or arrays within MongoDB.
 */
public class MongoCollectionHandler implements CollectionHandler {

    private final MongoDatabase database;

    public MongoCollectionHandler(MongoDatabase database) {
        this.database = database;
    }

    // ==================== Collection Operations ====================

    @Override
    public <T, ID> void insertCollection(
        ID parentId,
        String fieldName,
        Collection<?> values,
        Class<?> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.set(fieldName, values);
        
        collection.updateOne(filter, update);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID, C extends Collection<T>> C fetchCollection(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        CollectionKind kind,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Document doc = collection.find(filter).first();
        
        if (doc == null) {
            return (C) createEmptyCollection(kind);
        }
        
        Object fieldValue = doc.get(fieldName);
        if (fieldValue == null) {
            return (C) createEmptyCollection(kind);
        }
        
        // MongoDB returns ArrayList by default
        if (fieldValue instanceof List<?> list) {
            return (C) convertToCollectionKind(list, kind);
        }
        
        return (C) createEmptyCollection(kind);
    }

    @Override
    public <T, ID> void deleteFromCollection(
        ID parentId,
        String fieldName,
        T element,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.pull(fieldName, element);
        
        collection.updateOne(filter, update);
    }

    @Override
    public <T, ID> void deleteAllFromCollection(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.unset(fieldName);
        
        collection.updateOne(filter, update);
    }

    // ==================== Array Operations ====================

    @Override
    public <T, ID> void insertArray(
        ID parentId,
        String fieldName,
        Object arrayValue,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        if (arrayValue == null) {
            return;
        }

        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        // Convert array to list for MongoDB
        int length = Array.getLength(arrayValue);
        List<Object> mongoList = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            mongoList.add(Array.get(arrayValue, i));
        }
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.set(fieldName, mongoList);
        
        collection.updateOne(filter, update);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID> T[] fetchArray(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Document doc = collection.find(filter).first();
        
        if (doc == null) {
            return (T[]) Array.newInstance(elementType, 0);
        }
        
        Object fieldValue = doc.get(fieldName);
        if (fieldValue == null) {
            return (T[]) Array.newInstance(elementType, 0);
        }
        
        if (fieldValue instanceof List<?> list) {
            T[] array = (T[]) Array.newInstance(elementType, list.size());
            for (int i = 0; i < list.size(); i++) {
                array[i] = (T) list.get(i);
            }
            return array;
        }
        
        return (T[]) Array.newInstance(elementType, 0);
    }

    @Override
    public <T, ID> void deleteArray(
        ID parentId,
        String fieldName,
        Class<T> elementType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.unset(fieldName);
        
        collection.updateOne(filter, update);
    }

    // ==================== Map Operations ====================

    @Override
    public <K, V, ID> void insertMap(
        ID parentId,
        String fieldName,
        Map<K, V> values,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        // Convert map to MongoDB document
        // MongoDB documents require string keys, so we convert keys to strings
        Document mapDoc = new Document();
        for (Map.Entry<K, V> entry : values.entrySet()) {
            String key = entry.getKey().toString();
            mapDoc.put(key, entry.getValue());
        }
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.set(fieldName, mapDoc);
        
        collection.updateOne(filter, update);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V, ID> Map<K, V> fetchMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Document doc = collection.find(filter).first();
        
        if (doc == null) {
            return new HashMap<>();
        }
        
        Object fieldValue = doc.get(fieldName);
        if (fieldValue == null) {
            return new HashMap<>();
        }
        
        if (fieldValue instanceof Document mapDoc) {
            Map<K, V> result = new HashMap<>();
            for (String key : mapDoc.keySet()) {
                K convertedKey = convertKey(key, keyType);
                V value = (V) mapDoc.get(key);
                result.put(convertedKey, value);
            }
            return result;
        }
        
        return new HashMap<>();
    }

    @Override
    public <K, V, ID> void insertMapEntry(
        ID parentId,
        String fieldName,
        K key,
        V value,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        String stringKey = key.toString();
        String dotPath = fieldName + "." + stringKey;
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.set(dotPath, value);
        
        collection.updateOne(filter, update);
    }

    @Override
    public <K, V, ID> void deleteFromMap(
        ID parentId,
        String fieldName,
        K key,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        String stringKey = key.toString();
        String dotPath = fieldName + "." + stringKey;
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.unset(dotPath);
        
        collection.updateOne(filter, update);
    }

    @Override
    public <K, V, ID> void deleteAllFromMap(
        ID parentId,
        String fieldName,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.unset(fieldName);
        
        collection.updateOne(filter, update);
    }

    // ==================== MultiMap Operations ====================

    @Override
    public <K, V, ID> void insertMultiMap(
        ID parentId,
        String fieldName,
        Map<K, ? extends Collection<V>> values,
        Class<K> keyType,
        Class<V> valueType,
        RepositoryModel<?, ID> repoInfo
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        // Convert multimap to MongoDB document with arrays
        Document multiMapDoc = new Document();
        for (Map.Entry<K, ? extends Collection<V>> entry : values.entrySet()) {
            String key = entry.getKey().toString();
            List<V> valueList = new ArrayList<>(entry.getValue());
            multiMapDoc.put(key, valueList);
        }
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Bson update = Updates.set(fieldName, multiMapDoc);
        
        collection.updateOne(filter, update);
    }

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
        MongoCollection<Document> collection = database.getCollection(repoInfo.tableName());
        
        Bson filter = createIdFilter(parentId, repoInfo);
        Document doc = collection.find(filter).first();
        
        if (doc == null) {
            return new HashMap<>();
        }
        
        Object fieldValue = doc.get(fieldName);
        if (fieldValue == null) {
            return new HashMap<>();
        }
        
        if (fieldValue instanceof Document multiMapDoc) {
            Map<K, C> result = new HashMap<>();
            for (String key : multiMapDoc.keySet()) {
                K convertedKey = convertKey(key, keyType);
                Object value = multiMapDoc.get(key);
                
                if (value instanceof List<?> list) {
                    C collectionValue = (C) convertToCollectionKind(list, kind);
                    result.put(convertedKey, collectionValue);
                }
            }
            return result;
        }
        
        return new HashMap<>();
    }

    // ==================== Helper Methods ====================

    /**
     * Create a filter for finding a document by its ID.
     */
    private <ID> Bson createIdFilter(ID parentId, RepositoryModel<?, ID> repoInfo) {
        String idFieldName = repoInfo.getPrimaryKey().columnName();
        
        // MongoDB uses "_id" as the default ID field
        if (idFieldName.equals("id")) {
            idFieldName = "_id";
        }
        
        return Filters.eq(idFieldName, parentId);
    }

    /**
     * Create an empty collection of the specified kind.
     */
    private <T> Collection<T> createEmptyCollection(CollectionKind kind) {
        return switch (kind) {
            case LIST, OTHER -> new ArrayList<>();
            case SET -> new HashSet<>();
            case QUEUE -> new LinkedList<>();
            case DEQUE -> new ArrayDeque<>();
        };
    }

    /**
     * Convert a list to the specified collection kind.
     */
    @SuppressWarnings("unchecked")
    private <T> Collection<T> convertToCollectionKind(List<?> list, CollectionKind kind) {
        return switch (kind) {
            case OTHER -> (Collection<T>) new ArrayList<>(list);
            case SET -> (Collection<T>) new HashSet<>(list);
            case QUEUE -> (Collection<T>) new LinkedList<>(list);
            case DEQUE -> (Collection<T>) new ArrayDeque<>(list);
            case LIST -> (Collection<T>) list;
        };
    }

    /**
     * Convert a string key back to the original key type.
     */
    @SuppressWarnings("unchecked")
    private <K> K convertKey(String stringKey, Class<K> keyType) {
        if (keyType == String.class) {
            return (K) stringKey;
        }
        
        // Handle common key types
        if (keyType == Integer.class || keyType == int.class) {
            return (K) Integer.valueOf(stringKey);
        }
        if (keyType == Long.class || keyType == long.class) {
            return (K) Long.valueOf(stringKey);
        }
        if (keyType == Double.class || keyType == double.class) {
            return (K) Double.valueOf(stringKey);
        }
        if (keyType == Float.class || keyType == float.class) {
            return (K) Float.valueOf(stringKey);
        }
        if (keyType == Boolean.class || keyType == boolean.class) {
            return (K) Boolean.valueOf(stringKey);
        }
        
        // For enums
        if (keyType.isEnum()) {
            return (K) Enum.valueOf((Class<? extends Enum>) keyType, stringKey);
        }
        
        // Default: try to use string representation
        return (K) stringKey;
    }
}