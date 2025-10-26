package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.RelationalObjectFactory;
import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.api.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler.getOneToOneField;
import static io.github.flameyossnowy.universal.sql.internals.AbstractRelationalRepositoryAdapter.ADAPTERS;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public class ObjectFactory<T, ID> implements RelationalObjectFactory<T, ID> {
    protected final RepositoryInformation repoInfo;
    protected final DatabaseRelationshipHandler<T, ID> relationshipHandler;
    protected final SQLConnectionProvider connectionProvider;
    protected final Class<ID> idClass;
    protected final boolean hasPrimaryKey;
    protected final TypeResolverRegistry typeResolverRegistry;

    static final Unsafe UNSAFE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public ObjectFactory(RepositoryInformation repoInfo,
                         SQLConnectionProvider connectionProvider,
                         @NotNull RelationalRepositoryAdapter<T, ID> adapter,
                         TypeResolverRegistry typeResolverRegistry) {
        this.repoInfo = repoInfo;
        this.idClass = adapter.getIdType();
        this.connectionProvider = connectionProvider;
        this.typeResolverRegistry = typeResolverRegistry;
        this.relationshipHandler = new DatabaseRelationshipHandler<>(repoInfo, adapter.getIdType(), ADAPTERS, typeResolverRegistry, connectionProvider);
        this.hasPrimaryKey = repoInfo.getPrimaryKey() != null;
    }


    @Override
    public @NotNull T create(ResultSet rs) throws Exception {
        return repoInfo.isRecord() ? populateWithRecord(rs) : populateWithPojo(rs);
    }

    private @NotNull T populateWithRecord(ResultSet rs) throws Exception {
        Collection<FieldData<?>> components = repoInfo.getFields();
        Object[] args = new Object[components.size()];

        ID id = null;
        int index = 0;
        for (FieldData<?> field : components) {
            if (DatabaseObjectFactory.isRelationshipField(field)) {
                continue;
            }

            if (DatabaseObjectFactory.isListField(field)) {
                args[index] = handleGenericListField(field, id, rs);
                index++;
                continue;
            }

            if (DatabaseObjectFactory.isSetField(field)) {
                args[index] = handleGenericSetField(field, id, rs);
                index++;
                continue;
            }

            if (DatabaseObjectFactory.isMapField(field)) {
                MapData result = DatabaseObjectFactory.getMapData(field);

                if (result.isMultiMap()) {
                    args[index] = relationshipHandler.handleMultiMap(id, result.keyType(), result.valueType(), rs, field);
                    index++;
                    continue;
                }
                args[index] = relationshipHandler.handleNormalMap(id, result.keyType(), result.valueType(), rs, field);
                index++;
                continue;
            }

            if (field.type().isArray()) {
                args[index] = handleGenericArrayField(field, id, rs);
                index++;
                continue;
            }

            if (field.primary()) {
                Object value = resolveFieldValue(rs, field);
                if (value == null) throw new IllegalArgumentException("Primary key cannot be null.");
                id = (ID) value;
                args[index] = value;
                index++;
                continue;
            }

            Object value = resolveFieldValue(rs, field);
            if (value != null) args[index] = value;
            index++;
        }

        return (T) repoInfo.getRecordConstructor().newInstance(args);
    }

    private @NotNull T populateWithPojo(ResultSet rs) throws Exception {
        // Use MethodHandle for fast object creation if available
        T instance = (T) repoInfo.newInstance();
        
        ID id = null;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (DatabaseObjectFactory.isRelationshipField(field)) {
                continue;
            }

            if (DatabaseObjectFactory.isListField(field)) {
                setFieldValue(instance, field, handleGenericListField(field, id, rs));
                continue;
            }

            if (DatabaseObjectFactory.isSetField(field)) {
                setFieldValue(instance, field, handleGenericSetField(field, id, rs));
                continue;
            }

            if (DatabaseObjectFactory.isMapField(field)) {
                MapData result = DatabaseObjectFactory.getMapData(field);

                if (result.isMultiMap()) {
                    setFieldValue(instance, field, relationshipHandler.handleMultiMap(id, result.keyType(), result.valueType(), rs, field));
                    continue;
                }
                setFieldValue(instance, field, relationshipHandler.handleNormalMap(id, result.keyType(), result.valueType(), rs, field));
                continue;
            }

            if (field.type().isArray()) {
                setFieldValue(instance, field, handleGenericArrayField(field, id, rs));
                continue;
            }

            if (field.primary()) {
                Object value = resolveFieldValue(rs, field);
                if (value == null) throw new IllegalArgumentException("Primary key cannot be null.");
                id = (ID) value;
                setFieldValue(instance, field, value);
                continue;
            }

            populateFieldInternal(rs, field, instance);
        }

        return instance;
    }
    
    /**
     * Sets field value using MethodHandle if available, falls back to reflection.
     */
    private void setFieldValue(T instance, FieldData<?> field, Object value) {
        field.setValue(instance, value);
    }

    @Override
    public @NotNull T createWithRelationships(ResultSet rs) throws Exception {
        return populateRelationshipInstanceWithPojo(rs);
    }

    private @NotNull T populateRelationshipInstanceWithPojo(ResultSet rs) throws Exception {
        T instance = (T) repoInfo.newInstance();
        ID primaryId = hasPrimaryKey ? resolvePrimaryKey(rs) : null;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.primary()) {
                if (repoInfo.getPrimaryKey().equals(field)) {
                    field.setValue(instance, primaryId);
                    continue;
                }
                populateFieldInternal(rs, field, instance); // composite key
            } else if (field.oneToMany() != null && hasPrimaryKey) {
                field.setValue(instance, handleOneToManyField(field, primaryId));
            } else if (field.oneToOne() != null && hasPrimaryKey) {
                Object related = relationshipHandler.handleOneToOneRelationship(primaryId, field);

                OneToOneField backReference = getOneToOneField(
                        Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type())),
                        repoInfo
                );

                if (backReference == null) {

                    continue;
                }

                backReference.foundRelatedField().setValue(related, instance);
                field.setValue(instance, related);
            } else if (field.manyToOne() != null) {
                field.setValue(instance, relationshipHandler.handleManyToOneRelationship(rs, field));
            } else if (DatabaseObjectFactory.isListField(field) && hasPrimaryKey) {
                field.setValue(instance, handleGenericListField(field, primaryId, rs));
            } else if (DatabaseObjectFactory.isSetField(field) && hasPrimaryKey) {
                field.setValue(instance, handleGenericSetField(field, primaryId, rs));
            } else if (field.type().isArray()) {
                field.setValue(instance, handleGenericArrayField(field, primaryId, rs));
            } else if (DatabaseObjectFactory.isMapField(field) && hasPrimaryKey) {
                MapData result = DatabaseObjectFactory.getMapData(field);

                if (result.isMultiMap()) {
                    field.setValue(instance, relationshipHandler.handleMultiMap(primaryId, result.keyType(), result.valueType(), rs, field));
                    continue;
                }
                field.setValue(instance, relationshipHandler.handleNormalMap(primaryId, result.keyType(), result.valueType(), rs, field));
            } else {
                populateFieldInternal(rs, field, instance);
            }
        }

        return instance;
    }

    @Override
    public void insertEntity(PreparedStatement stmt, T entity) throws Exception {
        int paramIndex = 1;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.autoIncrement()) continue;
            if (DatabaseObjectFactory.isListField(field)
                    || DatabaseObjectFactory.isMapField(field)
                    || DatabaseObjectFactory.isSetField(field)) continue; // skip collections for now

            Object valueToInsert = DatabaseObjectFactory.resolveInsertValue(field, entity);
            SQLValueTypeResolver<Object> resolver = getValueResolver(field);

            Logging.deepInfo("Binding parameter " + paramIndex + ": " + valueToInsert);
            resolver.insert(stmt, paramIndex++, valueToInsert);
        }
    }

    @Override
    public void insertCollectionEntities(T entity, ID id, PreparedStatement statement) throws Exception {
        for (FieldData<?> field : repoInfo.getFields()) {
            if ((DatabaseObjectFactory.isListField(field) || DatabaseObjectFactory.isSetField(field)) && !field.isRelationship()) {
                handleLists(entity, id, field);
            } else if (DatabaseObjectFactory.isMapField(field)) {
                handleMaps(entity, id, field);
            }
        }
    }

    protected void handleMaps(T entity, ID id, FieldData<?> field) throws Exception {
        MapData result = DatabaseObjectFactory.getMapData(field);
        Class<Object> keyType = result.keyType();
        Class<Object> valueType = result.valueType();
        boolean isMultiMap = result.isMultiMap();

        if (isMultiMap) {
            MultiMapTypeResolver<Object, Object, ID> collectionTypeResolver =
                    SQLCollections.INSTANCE.getMultiMapResolver(keyType, valueType, idClass, connectionProvider, repoInfo, typeResolverRegistry);

            Map<Object, List<Object>> map = field.getValue(entity);
            collectionTypeResolver.insert(id, map);
        } else {
            MapTypeResolver<Object, Object, ID> collectionTypeResolver =
                    SQLCollections.INSTANCE.getMapResolver(keyType, valueType, idClass, connectionProvider, repoInfo,typeResolverRegistry);

            Map<Object, Object> map = field.getValue(entity);
            collectionTypeResolver.insert(id, map);
        }
    }


    protected void handleLists(T entity, ID id, FieldData<?> field) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();

        Class<Object> type = (Class<Object>) paramType.getActualTypeArguments()[0];
        CollectionTypeResolver<Object, ID> collectionTypeResolver =
                SQLCollections.INSTANCE.getResolver(type, idClass, connectionProvider, repoInfo, typeResolverRegistry);

        Collection<Object> collection = field.getValue(entity);
        collectionTypeResolver.insert(id, collection);
    }

    // Helpers

    protected Collection<Object> handleGenericListField(@NotNull FieldData<?> field, ID id, ResultSet set) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        return relationshipHandler.handleNormalLists(id, itemType, set, field);
    }

    protected Object[] handleGenericArrayField(@NotNull FieldData<?> field, ID id, ResultSet set) throws Exception {
        Class<?> itemType = field.type().getComponentType();
        return relationshipHandler.handleNormalArrays(id, itemType, set, field);
    }

    protected Collection<Object> handleGenericSetField(FieldData<?> field, ID id, ResultSet set) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        return relationshipHandler.handleNormalSets(id, itemType, set, field);
    }

    protected List<Object> handleOneToManyField(FieldData<?> field, ID primaryId) {
        return relationshipHandler.handleOneToManyRelationship(field, primaryId);
    }

    protected ID resolvePrimaryKey(ResultSet rs) throws Exception {
        FieldData<?> pkField = repoInfo.getPrimaryKey();
        SQLValueTypeResolver<ID> resolver = (SQLValueTypeResolver<ID>) typeResolverRegistry.getResolver(pkField.type());
        return resolver.resolve(rs, pkField.name());
    }

    void populateFieldInternal(ResultSet rs, FieldData<?> field, Object instance) throws Exception {
        Object value = resolveFieldValue(rs, field);
        if (value != null) field.setValue(instance, value);
    }

    protected Object resolveFieldValue(ResultSet rs, FieldData<?> field) throws Exception {
        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) typeResolverRegistry.getResolver(field.type());
        return resolver.resolve(rs, field.name());
    }

    protected SQLValueTypeResolver<Object> getValueResolver(FieldData<?> field) {
        RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());

        if (relatedInfo != null) {
            FieldData<?> pkField = relatedInfo.getPrimaryKey();
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>)
                    typeResolverRegistry.getResolver(pkField.type());

            return new MySQLValueTypeResolver(pkField, resolver);
        }

        return (SQLValueTypeResolver<Object>) typeResolverRegistry.getResolver(field.type());
    }

    private record MySQLValueTypeResolver(FieldData<?> pkField, SQLValueTypeResolver<Object> resolver) implements SQLValueTypeResolver<Object> {
        @Override
        public void insert(PreparedStatement ps, int index, Object related) throws Exception {
            Object relatedId = (related != null) ? pkField.getValue(related) : null;
            resolver.insert(ps, index, relatedId);
            Logging.deepInfo("Inserted related PK: " + relatedId);
        }

        @Override
        public Class<?> encodedType() {
            return null;
        }

        @Override
        public Object resolve(ResultSet rs, String columnLabel) throws Exception {
            return resolver.resolve(rs, columnLabel);
        }
    }
}
