package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.RelationalObjectFactory;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.DatabaseImplementation;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static io.github.flameyossnowy.universal.api.handler.AbstractRelationshipHandler.getOneToOneField;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public sealed abstract class ObjectFactory<T, ID>
    implements RelationalObjectFactory<T, ID>
    permits ArraySupportingObjectFactory, NoArrayObjectFactory {

    /* ============================================================
       Shared state
       ============================================================ */

    protected final RepositoryInformation repoInfo;
    protected final DatabaseRelationshipHandler<T, ID> relationshipHandler;
    protected final SQLConnectionProvider connectionProvider;
    protected final DatabaseImplementation implementation;
    protected final Class<ID> idClass;
    protected final boolean hasPrimaryKey;
    protected final TypeResolverRegistry typeResolverRegistry;

    protected ObjectFactory(
        RepositoryInformation repoInfo,
        SQLConnectionProvider connectionProvider,
        DatabaseImplementation implementation,
        RepositoryAdapter<T, ID, Connection> adapter,
        TypeResolverRegistry typeResolverRegistry
    ) {
        this.repoInfo = repoInfo;
        this.connectionProvider = connectionProvider;
        this.implementation = implementation;
        this.idClass = adapter.getIdType();
        this.typeResolverRegistry = typeResolverRegistry;
        this.relationshipHandler =
            new DatabaseRelationshipHandler<>(repoInfo, idClass, typeResolverRegistry, connectionProvider);
        this.hasPrimaryKey = repoInfo.getPrimaryKey() != null;
    }

    public RelationshipHandler<T, ID> getRelationshipHandler() {
        return relationshipHandler;
    }

    /* ============================================================
       Creation entry points
       ============================================================ */

    @Override
    public @NotNull T create(ResultSet rs) throws Exception {
        return repoInfo.isRecord()
            ? populateWithRecord(rs)
            : populateWithPojo(rs);
    }

    @Override
    public @NotNull T createWithRelationships(ResultSet rs) throws Exception {
        return populateRelationshipInstanceWithPojo(rs);
    }

    /* ============================================================
       Record population
       ============================================================ */

    private @NotNull T populateWithRecord(ResultSet rs) throws Exception {
        Collection<FieldData<?>> components = repoInfo.getFields();
        Object[] args = new Object[components.size()];

        ID id = null;
        int index = 0;

        SQLDatabaseResult sqlDatabaseResult = new SQLDatabaseResult(rs, typeResolverRegistry);
        for (FieldData<?> field : components) {
            if (DatabaseObjectFactory.isRelationshipField(field)) continue;

            if (DatabaseObjectFactory.isListField(field)) {
                args[index++] = readListField(field, id, rs);
                continue;
            }

            if (DatabaseObjectFactory.isSetField(field)) {
                args[index++] = readSetField(field, id, rs);
                continue;
            }

            if (DatabaseObjectFactory.isMapField(field)) {
                MapData map = DatabaseObjectFactory.getMapData(field);
                args[index++] = map.isMultiMap()
                    ? relationshipHandler.handleMultiMap(id, map.keyType(), map.valueType(), map.collectionKind())
                    : relationshipHandler.handleNormalMap(id, map.keyType(), map.valueType());
                continue;
            }

            if (field.type().isArray()) {
                args[index++] = readArrayField(field, id, rs);
                continue;
            }

            Object value = resolveFieldValue(field, sqlDatabaseResult);
            if (field.primary()) {
                if (value == null) throw new IllegalArgumentException("Primary key cannot be null.");
                id = (ID) value;
            }

            args[index++] = value;
        }

        return (T) repoInfo.getRecordConstructor().newInstance(args);
    }

    /* ============================================================
       POJO population
       ============================================================ */

    private @NotNull T populateWithPojo(ResultSet rs) throws Exception {
        T instance = (T) repoInfo.newInstance();
        ID id = null;

        SQLDatabaseResult sqlDatabaseResult = new SQLDatabaseResult(rs, typeResolverRegistry);
        for (FieldData<?> field : repoInfo.getFields()) {
            if (DatabaseObjectFactory.isRelationshipField(field)) continue;

            if (DatabaseObjectFactory.isListField(field)) {
                field.setValue(instance, readListField(field, id, rs));
                continue;
            }

            if (DatabaseObjectFactory.isSetField(field)) {
                field.setValue(instance, readSetField(field, id, rs));
                continue;
            }

            if (DatabaseObjectFactory.isMapField(field)) {
                MapData map = DatabaseObjectFactory.getMapData(field);
                field.setValue(
                    instance,
                    map.isMultiMap()
                        ? relationshipHandler.handleMultiMap(id, map.keyType(), map.valueType(), map.collectionKind())
                        : relationshipHandler.handleNormalMap(id, map.keyType(), map.valueType())
                );
                continue;
            }

            if (field.type().isArray()) {
                field.setValue(instance, readArrayField(field, id, rs));
                continue;
            }

            Object value = resolveFieldValue(field, sqlDatabaseResult);
            if (field.primary()) {
                if (value == null) throw new IllegalArgumentException("Primary key cannot be null.");
                id = (ID) value;
            }
            if (value != null) field.setValue(instance, value);
        }

        return instance;
    }

    private @NotNull T populateRelationshipInstanceWithPojo(ResultSet rs) throws Exception {
        T instance = (T) repoInfo.newInstance();

        SQLDatabaseResult sqlDatabaseResult = new SQLDatabaseResult(rs, typeResolverRegistry);
        ID primaryId = hasPrimaryKey ? resolvePrimaryKey(sqlDatabaseResult) : null;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.primary()) {
                field.setValue(instance, primaryId);
                continue;
            }

            if (field.oneToMany() != null && hasPrimaryKey) {
                field.setValue(instance, relationshipHandler.handleOneToManyRelationship(field, primaryId));
            } else if (field.oneToOne() != null && hasPrimaryKey) {
                Object related = relationshipHandler.handleOneToOneRelationship(primaryId, field);
                field.setValue(instance, related);

                if (related != null) {
                    RepositoryInformation relatedInfo =
                        Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type()));
                    OneToOneField backRef = getOneToOneField(relatedInfo, repoInfo);
                    if (backRef != null) backRef.foundRelatedField().setValue(related, instance);
                }
            } else if (field.manyToOne() != null) {
                field.setValue(instance, relationshipHandler.handleManyToOneRelationship(primaryId, field));
            } else if (DatabaseObjectFactory.isListField(field) && hasPrimaryKey) {
                field.setValue(instance, readListField(field, primaryId, rs));
            } else if (DatabaseObjectFactory.isSetField(field) && hasPrimaryKey) {
                field.setValue(instance, readSetField(field, primaryId, rs));
            } else if (field.type().isArray()) {
                field.setValue(instance, readArrayField(field, primaryId, rs));
            } else if (DatabaseObjectFactory.isMapField(field) && hasPrimaryKey) {
                MapData map = DatabaseObjectFactory.getMapData(field);
                field.setValue(
                    instance,
                    map.isMultiMap()
                        ? relationshipHandler.handleMultiMap(primaryId, map.keyType(), map.valueType(), map.collectionKind())
                        : relationshipHandler.handleNormalMap(primaryId, map.keyType(), map.valueType())
                );
            } else {
                populateFieldInternal(field, instance, sqlDatabaseResult);
            }
        }

        return instance;
    }

    @Override
    public void insertEntity(DatabaseParameters stmt, T entity) throws Exception {
        int paramIndex = 1;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.autoIncrement()) continue;

            if (DatabaseObjectFactory.isMapField(field)) {
                continue;
            }

            if (DatabaseObjectFactory.isListField(field) || DatabaseObjectFactory.isSetField(field)) {
                if (bindCollection(stmt, entity, field, paramIndex)) {
                    paramIndex++;
                }
                continue;
            }

            if (field.type().isArray()) {
                if (bindArray(stmt, entity, field, paramIndex)) {
                    paramIndex++;
                }
                continue;
            }

            Object valueToInsert = field.getValue(entity);
            if ((field.oneToOne() != null || field.manyToOne() != null)) {
                RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());
                if (relatedInfo != null) {
                    FieldData<?> pkField = relatedInfo.getPrimaryKey();
                    if (pkField == null) {
                        throw new IllegalArgumentException("Primary key must not be null");
                    }

                    TypeResolver<Object> resolver = (TypeResolver<Object>) typeResolverRegistry.resolve(pkField.type());
                    Objects.requireNonNull(resolver);

                    if (valueToInsert == null) {
                        resolver.insert(stmt, field.name(), null);
                        continue;
                    }

                    valueToInsert = pkField.getValue(valueToInsert);
                    resolver.insert(stmt, field.name(), valueToInsert);
                    paramIndex++;
                    continue;
                }
            }

            if (field.isRelationship()) continue;

            TypeResolver<Object> resolver = getTypeResolverForField(field, valueToInsert);
            if (resolver == null) continue;

            Logging.deepInfo("Binding " + field.name() + " -> " + valueToInsert);
            resolver.insert(stmt, field.name(), valueToInsert);
            paramIndex++;
        }
    }

    @Override
    public void insertCollectionEntities(T entity, ID id, DatabaseParameters statement) throws Exception {
        for (FieldData<?> field : repoInfo.getFields()) {
            if ((DatabaseObjectFactory.isListField(field) || DatabaseObjectFactory.isSetField(field))
                    && !field.isRelationship()) {
                handleLists(entity, id, field);
            } else if (DatabaseObjectFactory.isMapField(field)) {
                handleMaps(entity, id, field);
            }
        }
    }

    /* ============================================================
       Abstract capability hooks
       ============================================================ */

    protected abstract Collection<Object> readListField(
        FieldData<?> field, ID id, ResultSet rs
    ) throws Exception;

    protected abstract Collection<Object> readSetField(
        FieldData<?> field, ID id, ResultSet rs
    ) throws Exception;

    protected abstract Object[] readArrayField(
        FieldData<?> field, ID id, ResultSet rs
    ) throws Exception;

    protected abstract boolean bindCollection(
        DatabaseParameters stmt, T entity, FieldData<?> field, int paramIndex
    ) throws SQLException;

    protected abstract boolean bindArray(
        DatabaseParameters stmt, T entity, FieldData<?> field, int paramIndex
    ) throws SQLException;

    /* ============================================================
       Shared helpers
       ============================================================ */

    protected void handleLists(T entity, ID id, @NotNull FieldData<?> field) throws Exception {
        Class<Object> itemType = (Class<Object>) field.elementType();

        SQLCollections.INSTANCE
            .getResolver(itemType, idClass, connectionProvider, repoInfo, typeResolverRegistry)
            .insert(id, field.getValue(entity));
    }

    protected void handleMaps(T entity, ID id, FieldData<?> field) throws Exception {
        MapData map = DatabaseObjectFactory.getMapData(field);

        if (map.isMultiMap()) {
            SQLCollections.INSTANCE
                .getMultiMapResolver(map.keyType(), map.valueType(), idClass,
                    connectionProvider, repoInfo, typeResolverRegistry)
                .insert(id, field.getValue(entity));
        } else {
            SQLCollections.INSTANCE
                .getMapResolver(map.keyType(), map.valueType(), idClass,
                    connectionProvider, repoInfo, typeResolverRegistry)
                .insert(id, field.getValue(entity));
        }
    }

    protected ID resolvePrimaryKey(SQLDatabaseResult result) {
        FieldData<?> pk = repoInfo.getPrimaryKey();
        TypeResolver<ID> resolver = (TypeResolver<ID>) typeResolverRegistry.resolve(pk.type());
        return resolver.resolve(result, pk.name());
    }

    protected void populateFieldInternal(FieldData<?> field, Object instance, SQLDatabaseResult result) {
        Object value = resolveFieldValue(field, result);
        if (value != null) field.setValue(instance, value);
    }

    protected Object resolveFieldValue(@NotNull FieldData<?> field, SQLDatabaseResult result) {
        RepositoryInformation related = RepositoryMetadata.getMetadata(field.type());
        FieldData<?> target = related != null ? related.getPrimaryKey() : field;

        TypeResolver<Object> resolver =
            (TypeResolver<Object>) typeResolverRegistry.resolve(target.type());

        return resolver.resolve(result, field.name());
    }

    private TypeResolver<Object> getTypeResolverForField(@NotNull FieldData<?> field, Object value) {
        if ((field.oneToOne() != null || field.manyToOne() != null) && value != null) {
            RepositoryInformation related = RepositoryMetadata.getMetadata(field.type());
            return (TypeResolver<Object>) typeResolverRegistry.resolve(
                related.getPrimaryKey().type()
            );
        }
        return (TypeResolver<Object>) typeResolverRegistry.resolve(field.type());
    }
}
