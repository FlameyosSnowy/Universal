package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.RelationalObjectFactory;
import io.github.flameyossnowy.universal.api.cache.LazyArrayList;
import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.api.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.resolvers.*;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;

import static io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler.getOneToOneField;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public class ObjectFactory<T, ID> implements RelationalObjectFactory<T, ID> {
    protected final RepositoryInformation repoInfo;
    protected final DatabaseRelationshipHandler<T, ID> relationshipHandler;
    protected final SQLConnectionProvider connectionProvider;
    protected final Class<ID> idClass;
    protected final boolean hasPrimaryKey;
    protected final TypeResolverRegistry typeResolverRegistry;

    public ObjectFactory(RepositoryInformation repoInfo,
                         SQLConnectionProvider connectionProvider,
                         @NotNull RelationalRepositoryAdapter<T, ID> adapter,
                         TypeResolverRegistry typeResolverRegistry) {
        this.repoInfo = repoInfo;
        this.idClass = adapter.getIdType();
        this.connectionProvider = connectionProvider;
        this.typeResolverRegistry = typeResolverRegistry;
        this.relationshipHandler = new DatabaseRelationshipHandler<>(repoInfo, adapter.getIdType(), typeResolverRegistry, connectionProvider);
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
                    args[index] = relationshipHandler.handleMultiMap(id, result.keyType(), result.valueType());
                    index++;
                    continue;
                }
                args[index] = relationshipHandler.handleNormalMap(id, result.keyType(), result.valueType());
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
                    setFieldValue(instance, field, relationshipHandler.handleMultiMap(id, result.keyType(), result.valueType()));
                    continue;
                }
                setFieldValue(instance, field, relationshipHandler.handleNormalMap(id, result.keyType(), result.valueType()));
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
                RepositoryInformation metadata = Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));
                field.setValue(instance, related);
                if (related != null) {
                    RepositoryInformation relatedInfo = Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));
                    OneToOneField backReference = getOneToOneField(relatedInfo, repoInfo);
                    if (backReference != null) {
                        backReference.foundRelatedField().setValue(related, instance);
                    }
                }
            } else if (field.manyToOne() != null) {
                field.setValue(instance, relationshipHandler.handleManyToOneRelationship(primaryId, field));
            } else if (DatabaseObjectFactory.isListField(field) && hasPrimaryKey) {
                field.setValue(instance, handleGenericListField(field, primaryId, rs));
            } else if (DatabaseObjectFactory.isSetField(field) && hasPrimaryKey) {
                field.setValue(instance, handleGenericSetField(field, primaryId, rs));
            } else if (field.type().isArray()) {
                field.setValue(instance, handleGenericArrayField(field, primaryId, rs));
            } else if (DatabaseObjectFactory.isMapField(field) && hasPrimaryKey) {
                MapData result = DatabaseObjectFactory.getMapData(field);

                if (result.isMultiMap()) {
                    field.setValue(instance, relationshipHandler.handleMultiMap(primaryId, result.keyType(), result.valueType()));
                    continue;
                }
                field.setValue(instance, relationshipHandler.handleNormalMap(primaryId, result.keyType(), result.valueType()));
            } else {
                populateFieldInternal(rs, field, instance);
            }
        }



        return instance;
    }

    @Override
    public void insertEntity(DatabaseParameters stmt, T entity) throws Exception {
        // First handle direct fields (non-relationships and foreign keys)
        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.autoIncrement() || field.oneToMany() != null) {
                continue; // Skip auto-incrementing fields and collection-based relationships
            }

            if (DatabaseObjectFactory.isListField(field) || DatabaseObjectFactory.isMapField(field) || DatabaseObjectFactory.isSetField(field)) {
                continue; // Skip collections for now
            }

            Object valueToInsert = field.getValue(entity);
            Logging.deepInfo("Processing field: " + field.name() + " with value: " + valueToInsert);

            // Handle relationship fields (OneToOne, ManyToOne)
            if ((field.oneToOne() != null || field.manyToOne() != null)) {
                RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());
                if (relatedInfo != null) {
                    FieldData<?> pkField = relatedInfo.getPrimaryKey();
                    TypeResolver<Object> resolver = (TypeResolver<Object>) typeResolverRegistry.resolve(pkField.type());
                    Objects.requireNonNull(resolver);

                    // If null, bind null explicitly
                    if (valueToInsert == null) {
                        resolver.insert(stmt, field.name(), null);
                        continue;
                    }

                    // Otherwise: bind the foreign key ID value
                    valueToInsert = pkField.getValue(valueToInsert);
                    resolver.insert(stmt, field.name(), valueToInsert);
                    continue;
                }
            }


            // Get the appropriate type resolver
            TypeResolver<Object> resolver = getTypeResolverForField(field, valueToInsert);
            if (resolver == null) {
                Logging.deepInfo("No resolver for " + field.type() + ", assuming it's a relationship handled elsewhere.");
                continue;
            }

            Logging.deepInfo("Binding parameter " + field.name() + ": " + valueToInsert);
            resolver.insert(stmt, field.name(), valueToInsert);
        }
    }

    private TypeResolver<Object> getTypeResolverForField(FieldData<?> field, Object value) {
        if ((field.oneToOne() != null || field.manyToOne() != null) && value != null) {
            RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());
            if (relatedInfo != null) {
                FieldData<?> pkField = relatedInfo.getPrimaryKey();
                return (TypeResolver<Object>) typeResolverRegistry.resolve(pkField.type());
            }
        }
        return (TypeResolver<Object>) typeResolverRegistry.resolve(field.type());
    }

    @Override
    public void insertCollectionEntities(T entity, ID id, DatabaseParameters statement) throws Exception {
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
                    SQLCollections.INSTANCE.getMapResolver(keyType, valueType, idClass, connectionProvider, repoInfo, typeResolverRegistry);

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
        return relationshipHandler.handleNormalLists(id, itemType);
    }

    protected Object[] handleGenericArrayField(@NotNull FieldData<?> field, ID id, ResultSet set) throws Exception {
        Class<?> itemType = field.type().getComponentType();
        return relationshipHandler.handleNormalArrays(id, itemType);
    }

    protected Collection<Object> handleGenericSetField(FieldData<?> field, ID id, ResultSet set) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        return relationshipHandler.handleNormalSets(id, itemType);
    }

    protected List<Object> handleOneToManyField(FieldData<?> field, ID primaryId) {
        return relationshipHandler.handleOneToManyRelationship(field, primaryId);
    }

    protected ID resolvePrimaryKey(ResultSet rs) {
        FieldData<?> pkField = repoInfo.getPrimaryKey();
        TypeResolver<ID> resolver = (TypeResolver<ID>) typeResolverRegistry.getResolver(pkField.type());
        SQLDatabaseResult result = new SQLDatabaseResult(rs, typeResolverRegistry);
        return resolver.resolve(result, pkField.name());
    }

    void populateFieldInternal(ResultSet rs, FieldData<?> field, Object instance) {
        Object value = resolveFieldValue(rs, field);
        if (value != null) field.setValue(instance, value);
    }

    protected Object resolveFieldValue(ResultSet rs, FieldData<?> field) {
        RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());

        if (relatedInfo != null) {
            FieldData<?> pkField = relatedInfo.getPrimaryKey();
            TypeResolver<Object> resolver = (TypeResolver<Object>) typeResolverRegistry.getResolver(pkField.type());
            SQLDatabaseResult result = new SQLDatabaseResult(rs, typeResolverRegistry);
            return resolver.resolve(result, field.name());
        }

        TypeResolver<Object> resolver = (TypeResolver<Object>) typeResolverRegistry.getResolver(field.type());
        SQLDatabaseResult result = new SQLDatabaseResult(rs, typeResolverRegistry);
        return resolver.resolve(result, field.name());
    }

    @Nullable
    protected TypeResolver<Object> getValueResolver(@NotNull FieldData<?> field) {
        RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());

        if (relatedInfo != null) {
            FieldData<?> pkField = relatedInfo.getPrimaryKey();
            var resolver = (TypeResolver<Object>) typeResolverRegistry.resolve(pkField.type());

            return new MySQLValueTypeResolver(pkField, resolver);
        }

        return (TypeResolver<Object>) typeResolverRegistry.getResolver(field.type());
    }

    private record MySQLValueTypeResolver(FieldData<?> pkField, TypeResolver<Object> resolver) implements TypeResolver<Object> {
        @Override
        public Class<Object> getType() {
            return (Class<Object>) pkField.type();
        }

        @Override
        public Class<?> getDatabaseType() {
            return pkField.type();
        }

        @Override
        public Object resolve(DatabaseResult result, String columnName) {
            return resolver.resolve(result, columnName);
        }

        @Override
        public void insert(DatabaseParameters parameters, String index, Object value) {
            resolver.insert(parameters, index, value);
        }
    }
}
