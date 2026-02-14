package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.resolvers.CollectionTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MapTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.MultiMapTypeResolver;

import java.lang.reflect.Array;
import java.util.*;

/**
 * SQL implementation of CollectionHandler.
 * Delegates to type-specific resolvers from SQLCollections.
 */
@SuppressWarnings("unchecked")
public class SQLCollectionHandler implements CollectionHandler {
    private final SQLConnectionProvider connectionProvider;
    private final TypeResolverRegistry resolverRegistry;
    private final boolean supportsArrays;
    
    public SQLCollectionHandler(
        SQLConnectionProvider connectionProvider,
        TypeResolverRegistry resolverRegistry, boolean supportsArrays
    ) {
        this.connectionProvider = connectionProvider;
        this.resolverRegistry = resolverRegistry;
        this.supportsArrays = supportsArrays;
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

        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        CollectionTypeResolver<Object, ID> resolver = (CollectionTypeResolver<Object, ID>) SQLCollections.INSTANCE.getResolver(
            elementType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );
        
        
        Collection<Object> typedValues = (Collection<Object>) values;
        try {
            resolver.insert(parentId, typedValues);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public <T, ID, C extends Collection<T>> C fetchCollection(
            ID parentId,
            String fieldName,
            Class<T> elementType,
            CollectionKind kind,
            RepositoryModel<?, ID> repoInfo
    ) {
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        CollectionTypeResolver<T, ID> resolver = SQLCollections.INSTANCE.getResolver(
            elementType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );
        
        return resolver.resolve(parentId, kind);
    }
    
    @Override
    public <T, ID> void deleteFromCollection(
            ID parentId,
            String fieldName,
            T element,
            Class<T> elementType,
            RepositoryModel<?, ID> repoInfo
    ) {
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        CollectionTypeResolver<T, ID> resolver = SQLCollections.INSTANCE.getResolver(
            elementType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.delete(parentId, element);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public <T, ID> void deleteAllFromCollection(
            ID parentId,
            String fieldName,
            Class<T> elementType,
            RepositoryModel<?, ID> repoInfo
    ) {
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        CollectionTypeResolver<T, ID> resolver = SQLCollections.INSTANCE.getResolver(
            elementType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.deleteAll(parentId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        
        // Convert array to collection
        int length = Array.getLength(arrayValue);
        List<T> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            
            T element = (T) Array.get(arrayValue, i);
            list.add(element);
        }
        
        insertCollection(parentId, fieldName, list, elementType, repoInfo);
    }
    
    @Override
    public <T, ID> T[] fetchArray(
            ID parentId,
            String fieldName,
            Class<T> elementType,
            RepositoryModel<?, ID> repoInfo
    ) {
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        CollectionTypeResolver<T, ID> resolver = SQLCollections.INSTANCE.getResolver(
            elementType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );
        
        return resolver.resolveArray(parentId);
    }
    
    @Override
    public <T, ID> void deleteArray(
            ID parentId,
            String fieldName,
            Class<T> elementType,
            RepositoryModel<?, ID> repoInfo
    ) {
        deleteAllFromCollection(parentId, fieldName, elementType, repoInfo);
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
        
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.insert(parentId, values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public <K, V, ID> Map<K, V> fetchMap(
            ID parentId,
            String fieldName,
            Class<K> keyType,
            Class<V> valueType,
            RepositoryModel<?, ID> repoInfo
    ) {
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );
        
        return resolver.resolve(parentId);
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
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.insert(parentId, key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.delete(parentId, key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public <K, V, ID> void deleteAllFromMap(
            ID parentId,
            String fieldName,
            Class<K> keyType,
            Class<V> valueType,
            RepositoryModel<?, ID> repoInfo
    ) {
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.delete(parentId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MultiMapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMultiMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );

        try {
            resolver.insert(parentId, values);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public <K, V, ID, C extends Collection<V>> Map<K, C> fetchMultiMap(
            ID parentId,
            String fieldName,
            Class<K> keyType,
            Class<V> valueType,
            CollectionKind kind,
            RepositoryModel<?, ID> repoInfo
    ) {
        
        Class<ID> idType = (Class<ID>) parentId.getClass();
        
        MultiMapTypeResolver<K, V, ID> resolver = SQLCollections.INSTANCE.getMultiMapResolver(
            keyType,
            valueType,
            idType,
            connectionProvider,
            repoInfo,
            resolverRegistry,
            this,
            supportsArrays
        );
        
        return resolver.resolve(parentId, kind);
    }
}