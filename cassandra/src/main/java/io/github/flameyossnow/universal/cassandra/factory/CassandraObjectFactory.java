package io.github.flameyossnow.universal.cassandra.factory;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import io.github.flameyossnow.universal.cassandra.collections.MultiMapTypeResolver;
import io.github.flameyossnow.universal.cassandra.handler.CassandraRelationshipHandler;
import io.github.flameyossnow.universal.cassandra.objects.CassandraDatabaseParameters;
import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.universal.api.utils.Logging;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@SuppressWarnings("unchecked")
public record CassandraObjectFactory<T, ID>(
        RepositoryInformation repositoryInformation, TypeResolverRegistry resolverRegistry,
        Class<ID> idClass, Class<T> repository, Session session, CassandraRelationshipHandler<T, ID> relationshipHandler
) implements DatabaseObjectFactory<T, Row> {
    @Override
    public T create(Row set) {
        @SuppressWarnings("unchecked")
        T instance = (T) repositoryInformation.newInstance();

        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (DatabaseObjectFactory.isListField(field)) {
                field.setValue(instance, handleGenericListField(field, set));
                continue;
            }

            if (DatabaseObjectFactory.isSetField(field)) {
                field.setValue(instance, handleGenericSetField(field, set));
                continue;
            }

            if (DatabaseObjectFactory.isMapField(field)) {
                field.setValue(instance, handleGenericMapField(field, set));
                continue;
            }

            Object o = set.get(field.name(), field.type());
            field.setValue(instance, o);
        }
        return instance;
    }

    @Override
    public T createWithRelationships(Row set) {
        return null;
    }

    public void insertEntity(DatabaseParameters stmt, T entity) {
        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.autoIncrement()) continue;
            if (DatabaseObjectFactory.isListField(field)
                    || DatabaseObjectFactory.isMapField(field)
                    || DatabaseObjectFactory.isSetField(field)) continue; // skip collections for now

            Object valueToInsert = DatabaseObjectFactory.resolveInsertValue(field, entity);

            @SuppressWarnings("unchecked")
            TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(field.type());

            Logging.deepInfo("Binding parameter " + field.name() + ": " + valueToInsert);
            resolver.insert(stmt, field.name(), valueToInsert);
        }
    }

    private List<?> handleGenericListField(@NotNull FieldData<?> field, Row set) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        
        TypeResolver<Object> itemResolver = (TypeResolver<Object>) resolverRegistry.getResolver(itemType);
        if (itemResolver == null) {
            return set.getList(field.name(), itemType);
        }
        
        Class<?> dbType = itemResolver.getDatabaseType();
        List<?> dbList = set.getList(field.name(), dbType);
        if (dbList == null || dbList.isEmpty()) {
            return dbList;
        }
        
        List<Object> convertedList = new ArrayList<>(dbList.size());
        for (Object dbElement : dbList) {
            Object element = convertFromDatabaseType(dbElement, itemResolver, field.name());
            convertedList.add(element);
        }
        
        return convertedList;
    }

    private Set<?> handleGenericSetField(FieldData<?> field, Row row) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        
        TypeResolver<Object> itemResolver = (TypeResolver<Object>) resolverRegistry.getResolver(itemType);
        if (itemResolver == null) {
            return row.getSet(field.name(), itemType);
        }
        
        Class<?> dbType = itemResolver.getDatabaseType();
        Set<?> dbSet = row.getSet(field.name(), dbType);
        if (dbSet == null || dbSet.isEmpty()) {
            return dbSet;
        }
        
        Set<Object> convertedSet = new HashSet<>(dbSet.size());
        for (Object dbElement : dbSet) {
            Object element = convertFromDatabaseType(dbElement, itemResolver, field.name());
            convertedSet.add(element);
        }
        
        return convertedSet;
    }

    private Map<?, ?> handleGenericMapField(FieldData<?> field, Row row) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Type[] arguments = paramType.getActualTypeArguments();
        Class<?> keyType = (Class<?>) arguments[0];
        Class<?> valueType = (Class<?>) arguments[1];
        
        TypeResolver<Object> keyResolver = (TypeResolver<Object>) resolverRegistry.getResolver(keyType);
        TypeResolver<Object> valueResolver = (TypeResolver<Object>) resolverRegistry.getResolver(valueType);
        
        if (keyResolver == null || valueResolver == null) {
            return row.getMap(field.name(), keyType, valueType);
        }
        
        Class<?> keyDbType = keyResolver.getDatabaseType();
        Class<?> valueDbType = valueResolver.getDatabaseType();
        
        Map<?, ?> dbMap = row.getMap(field.name(), keyDbType, valueDbType);
        if (dbMap == null || dbMap.isEmpty()) {
            return dbMap;
        }
        
        Map<Object, Object> convertedMap = new HashMap<>(dbMap.size());
        for (Map.Entry<?, ?> entry : dbMap.entrySet()) {
            Object key = convertFromDatabaseType(entry.getKey(), keyResolver, field.name());
            Object value = convertFromDatabaseType(entry.getValue(), valueResolver, field.name());
            convertedMap.put(key, value);
        }
        
        return convertedMap;
    }

    public void insertCollectionEntities(T entity, ID id, CassandraDatabaseParameters parameters) {
        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (DatabaseObjectFactory.isListField(field) && !field.isRelationship()) {
                handleLists(entity, field, parameters);
            } else if (DatabaseObjectFactory.isSetField(field) && !field.isRelationship()) {
                handleSets(entity, field, parameters);
            } else if (DatabaseObjectFactory.isMapField(field)) {
                handleMaps(entity, id, field, parameters);
            }
        }
    }

    private void handleLists(T entity, FieldData<?> field, CassandraDatabaseParameters parameters) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<Object> elementType = (Class<Object>) paramType.getActualTypeArguments()[0];

        List<Object> collection = field.getValue(entity);
        if (collection == null || collection.isEmpty()) {
            return;
        }

        TypeResolver<Object> elementResolver = resolverRegistry.getResolver(elementType);
        if (elementResolver == null) {
            throw new IllegalStateException("No resolver found for type: " + elementType.getName());
        }

        List<Object> convertedList = new ArrayList<>(collection.size());
        for (Object element : collection) {
            Object convertedElement = convertToDatabaseType(element, elementResolver);
            convertedList.add(convertedElement);
        }

        String fieldName = field.name();
        parameters.set(fieldName, convertedList, List.class);
    }

    private void handleMaps(T entity, ID id, FieldData<?> field, CassandraDatabaseParameters statement) {
        MapData result = DatabaseObjectFactory.getMapData(field);
        Class<Object> keyType = result.keyType();
        Class<Object> valueType = result.valueType();
        boolean isMultiMap = result.isMultiMap();

        if (isMultiMap) {
            MultiMapTypeResolver<Object, Object, ID> collectionTypeResolver =
                    CassandraCollections.INSTANCE.getMultiMapResolver(keyType, valueType, idClass, session, repositoryInformation, resolverRegistry);

            Map<Object, List<Object>> map = field.getValue(entity);
            collectionTypeResolver.insert(id, map);
        } else {
            // Handle regular maps
            TypeResolver<Object> keyResolver = resolverRegistry.getResolver(keyType);
            TypeResolver<Object> valueResolver = resolverRegistry.getResolver(valueType);

            if (keyResolver == null || valueResolver == null) {
                throw new IllegalStateException(String.format(
                        "No resolver found for key type %s or value type %s",
                        keyType.getName(), valueType.getName()));
            }

            Map<Object, Object> map = field.getValue(entity);
            if (map == null || map.isEmpty()) {
                return;
            }

            // Convert each key and value using their respective resolvers
            Map<Object, Object> convertedMap = new HashMap<>(map.size());
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                Object convertedKey = convertToDatabaseType(entry.getKey(), keyResolver);
                Object convertedValue = convertToDatabaseType(entry.getValue(), valueResolver);
                convertedMap.put(convertedKey, convertedValue);
            }

            String fieldName = field.name();
            statement.set(fieldName, convertedMap, Map.class);
        }
    }

    private void handleSets(T entity, FieldData<?> field, CassandraDatabaseParameters parameters) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<Object> elementType = (Class<Object>) paramType.getActualTypeArguments()[0];

        Set<Object> set = field.getValue(entity);
        if (set == null || set.isEmpty()) {
            return;
        }

        TypeResolver<Object> elementResolver = resolverRegistry.getResolver(elementType);
        if (elementResolver == null) {
            throw new IllegalStateException("No resolver found for type: " + elementType.getName());
        }

        Set<Object> convertedSet = new HashSet<>(set.size());
        for (Object element : set) {
            Object convertedElement = convertToDatabaseType(element, elementResolver);
            convertedSet.add(convertedElement);
        }

        String fieldName = field.name();
        parameters.set(fieldName, convertedSet, Set.class);
    }

    /**
     * Converts an application type value to its database representation using the provided resolver.
     * 
     * @param value the application value to convert
     * @param resolver the type resolver to use for conversion
     * @return the database representation of the value
     */
    private Object convertToDatabaseType(Object value, TypeResolver<Object> resolver) {
        if (value == null) {
            return null;
        }
        
        // Create a temporary DatabaseParameters to capture the converted value
        CassandraDatabaseParameters tempParams = new CassandraDatabaseParameters();
        resolver.insert(tempParams, "temp", value);
        
        // Extract the converted value
        Class<?> databaseType = resolver.getDatabaseType();
        return tempParams.get("temp", databaseType);
    }

    /**
     * Converts a database value back to its application type.
     * 
     * @param dbValue the database value to convert
     * @param resolver the type resolver to use for conversion
     * @param name the column name (used for resolver context)
     * @return the application type value
     */
    @SuppressWarnings("unchecked")
    private <A> A convertFromDatabaseType(Object dbValue, TypeResolver<Object> resolver, String name) {
        if (dbValue == null) {
            return null;
        }
        
        // Create a minimal DatabaseResult wrapper for the resolver
        DatabaseResult tempResult = new DatabaseResult() {
            @Override
            public <E> E get(String columnName, Class<E> type) {
                if (name.equals(columnName)) {
                    return type.cast(dbValue);
                }
                return null;
            }

            @Override
            public boolean hasColumn(String columnName) {
                return columnName.equals(name);
            }

            @Override
            public int getColumnCount() {
                return 1;
            }

            @Override
            public String getColumnName(int columnIndex) {
                return name;
            }
        };
        
        return (A) resolver.resolve(tempResult, name);
    }
}