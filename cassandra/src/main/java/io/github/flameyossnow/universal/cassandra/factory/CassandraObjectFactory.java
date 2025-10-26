package io.github.flameyossnow.universal.cassandra.factory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import io.github.flameyossnow.universal.cassandra.collections.MultiMapTypeResolver;
import io.github.flameyossnow.universal.cassandra.handler.CassandraRelationshipHandler;
import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public record CassandraObjectFactory<T, ID>(
        RepositoryInformation repositoryInformation, TypeResolverRegistry resolverRegistry,
        Class<ID> idClass, Class<T> repository, Session session, CassandraRelationshipHandler<T, ID> relationshipHandler
) implements DatabaseObjectFactory<T, ID, Row> {
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

    private static List<?> handleGenericListField(@NotNull FieldData<?> field, Row set) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        return set.getList(field.name(), itemType);
    }

    private static Set<?> handleGenericSetField(FieldData<?> field, Row row) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        return row.getSet(field.name(), itemType);
    }

    private static Map<?, ?> handleGenericMapField(FieldData<?> field, Row row) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Type[] arguments = paramType.getActualTypeArguments();
        Class<?> keyType = (Class<?>) arguments[0];
        Class<?> valueType = (Class<?>) arguments[1];
        return row.getMap(field.name(), keyType, valueType);
    }

    public void insertCollectionEntities(T entity, ID id, PreparedStatement statement) throws Exception {
        for (FieldData<?> field : repositoryInformation.getFields()) {
            if ((DatabaseObjectFactory.isListField(field) || DatabaseObjectFactory.isSetField(field)) && !field.isRelationship()) {
                handleLists(entity, id, field);
            } else if (DatabaseObjectFactory.isMapField(field)) {
                handleMaps(entity, id, field, statement);
            }
        }
    }

    private void handleMaps(T entity, ID id, FieldData<?> field, PreparedStatement statement) throws Exception {
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

        }
    }


    protected void handleLists(T entity, ID id, FieldData<?> field) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();

        Class<Object> type = (Class<Object>) paramType.getActualTypeArguments()[0];

    }
}