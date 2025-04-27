package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public final class ObjectFactory<T, ID> {

    private final RepositoryInformation repoInfo;
    private final DatabaseRelationshipHandler<T, ID> relationshipHandler;
    private final SQLConnectionProvider connectionProvider;
    private final Class<ID> idClass;
    private final boolean hasPrimaryKey;

    private static final Map<Class<?>, Supplier<Object>> NOW_MAPPERS = Map.ofEntries(
            Map.entry(Instant.class, Instant::now),
            Map.entry(Date.class, Date::new),
            Map.entry(java.sql.Time.class, () -> java.sql.Time.valueOf(LocalTime.now())),
            Map.entry(Timestamp.class, () -> new Timestamp(System.currentTimeMillis())),
            Map.entry(Year.class, Year::now),
            Map.entry(java.sql.Date.class, () -> new java.sql.Date(System.currentTimeMillis())),
            Map.entry(TimeZone.class, TimeZone::getDefault),
            Map.entry(Calendar.class, Calendar::getInstance),
            Map.entry(LocalDate.class, LocalDate::now),
            Map.entry(LocalDateTime.class, LocalDateTime::now),
            Map.entry(LocalTime.class, LocalTime::now),
            Map.entry(ZoneId.class, ZoneId::systemDefault),
            Map.entry(ZoneOffset.class, ZoneOffset::systemDefault),
            Map.entry(ZonedDateTime.class, ZonedDateTime::now)
    );

    public ObjectFactory(RepositoryInformation repoInfo,
                         SQLConnectionProvider connectionProvider,
                         @NotNull RelationalRepositoryAdapter<T, ID> adapter) {
        this.repoInfo = repoInfo;
        this.idClass = adapter.getIdType();
        this.connectionProvider = connectionProvider;
        this.relationshipHandler = new DatabaseRelationshipHandler<>(repoInfo, connectionProvider, adapter.getIdType());
        this.hasPrimaryKey = repoInfo.getPrimaryKey() != null;
    }

    public @NotNull T create(ResultSet rs) throws Exception {
        T instance = (T) repoInfo.newInstance();
        ID id = null;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (isRelationshipField(field)) {
                warnRelationshipInCreate(field);
                continue;
            }

            if (isListField(field)) {
                handleGenericListField(field, instance, id);
                continue;
            }

            if (isSetField(field)) {
                handleGenericSetField(field, instance, id);
                continue;
            }

            if (isMapField(field)) {
                handleGenericMapField(field, instance, id);
                continue;
            }

            if (field.primary()) {
                Object value = resolveFieldValue(rs, field);
                if (value == null) throw new IllegalArgumentException("Primary key cannot be null.");
                id = (ID) value;
                field.setValue(instance, value);
                continue;
            }

            populateFieldInternal(rs, field, instance);
        }

        return instance;
    }

    public @NotNull T createWithRelationships(ResultSet rs) throws Exception {
        T instance = (T) repoInfo.newInstance();
        ID primaryId = hasPrimaryKey ? resolvePrimaryKey(rs) : null;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.oneToMany() != null && hasPrimaryKey) {
                handleOneToManyField(field, instance, primaryId);
            } else if (field.oneToOne() != null && hasPrimaryKey) {
                relationshipHandler.handleOneToOneRelationship(rs, field, instance);
            } else if (field.manyToOne() != null) {
                relationshipHandler.handleManyToOneRelationship(rs, field, instance);
            } else if (isListField(field) && hasPrimaryKey) {
                handleGenericListField(field, instance, primaryId);
            } else if (isSetField(field) && hasPrimaryKey) {
                handleGenericSetField(field, instance, primaryId);
            } else if (isMapField(field) && hasPrimaryKey) {
                handleGenericMapField(field, instance, primaryId);
            } else {
                populateFieldInternal(rs, field, instance);
            }
        }

        return instance;
    }

    public void insertEntity(PreparedStatement stmt, T entity) throws Exception {
        int paramIndex = 1;

        ID id = null;

        // First pass: regular fields
        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.autoIncrement()) continue;
            if (isListField(field) || isMapField(field) || isSetField(field)) continue; // skip collections for now

            Object valueToInsert = resolveInsertValue(field, entity);
            if (valueToInsert.getClass().isAssignableFrom(idClass)) id = (ID) valueToInsert;

            SQLValueTypeResolver<Object> resolver = getValueResolver(field);

            Logging.deepInfo("Binding parameter " + paramIndex + ": " + valueToInsert);
            resolver.insert(stmt, paramIndex++, valueToInsert);
        }
    }

    public void insertCollectionEntities(T entity, ID id) throws Exception {
        for (FieldData<?> field : repoInfo.getFields()) {
            if (isListField(field) || isSetField(field)) {
                handleLists(entity, id, field);
            } else if (isMapField(field)) {
                handleMaps(entity, id, field);
            }
        }
    }

    private void handleMaps(T entity, ID id, FieldData<?> field) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();

        Type[] types = paramType.getActualTypeArguments();

        Type keyTypeRaw = types[0];
        Type valueTypeRaw = types[1];

        Class<Object> keyType = (Class<Object>) keyTypeRaw;
        Class<Object> valueType;

        boolean isMultiMap = false;

        if (valueTypeRaw instanceof ParameterizedType parameterizedValueType) {
            Type rawType = parameterizedValueType.getRawType();
            if (rawType instanceof Class<?> rawClass && List.class.isAssignableFrom(rawClass)) {
                isMultiMap = true;
                valueType = (Class<Object>) parameterizedValueType.getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + valueTypeRaw);
            }
        } else if (valueTypeRaw instanceof Class<?> valueClass) {
            valueType = (Class<Object>) valueClass;
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + valueTypeRaw);
        }

        if (isMultiMap) {
            MultiMapTypeResolver<Object, Object, ID> collectionTypeResolver =
                    SQLCollections.INSTANCE.getMultiMapResolver(keyType, valueType, idClass, connectionProvider, repoInfo);

            Map<Object, List<Object>> map = field.getValue(entity);
            collectionTypeResolver.insert(id, map);
        } else {
            MapTypeResolver<Object, Object, ID> collectionTypeResolver =
                    SQLCollections.INSTANCE.getMapResolver(keyType, valueType, idClass, connectionProvider, repoInfo);

            Map<Object, Object> map = field.getValue(entity);
            collectionTypeResolver.insert(id, map);
        }
    }


    private void handleLists(T entity, ID id, FieldData<?> field) throws Exception {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();

        Class<Object> type = (Class<Object>) paramType.getActualTypeArguments()[0];
        CollectionTypeResolver<Object, ID> collectionTypeResolver =
                SQLCollections.INSTANCE.getResolver(
                        type, idClass, connectionProvider, repoInfo);

        Collection<Object> collection = field.getValue(entity);
        collectionTypeResolver.insert(id, collection);
    }

    // Helpers

    private static boolean isRelationshipField(FieldData<?> field) {
        return field.oneToOne() != null || field.oneToMany() != null || field.manyToOne() != null;
    }

    private static boolean isListField(FieldData<?> field) {
        return List.class.isAssignableFrom(field.type());
    }

    private static boolean isSetField(FieldData<?> field) {
        return Set.class.isAssignableFrom(field.type());
    }

    private static boolean isMapField(FieldData<?> field) {
        return Map.class.isAssignableFrom(field.type());
    }

    private void warnRelationshipInCreate(FieldData<?> field) {
        Logging.error("`create` should not be used for relationships, use `createWithRelationships` instead.");
        Logging.error("Offending Field: " + repoInfo.getType().getSimpleName() + "#" + field.name());
    }

    private void handleGenericListField(FieldData<?> field, T instance, ID id) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        relationshipHandler.handleNormalLists(field, instance, id, itemType);
    }

    private void handleGenericSetField(FieldData<?> field, T instance, ID id) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Class<?> itemType = (Class<?>) paramType.getActualTypeArguments()[0];
        relationshipHandler.handleNormalSets(field, instance, id, itemType);
    }

    private void handleGenericMapField(FieldData<?> field, T instance, ID id) {
        Field rawField = field.rawField();
        ParameterizedType paramType = (ParameterizedType) rawField.getGenericType();
        Type[] types = paramType.getActualTypeArguments();

        Type keyTypeRaw = types[0];
        Type valueTypeRaw = types[1];

        Class<Object> keyType = (Class<Object>) keyTypeRaw;
        Class<Object> valueType;

        boolean isMultiMap = false;

        if (valueTypeRaw instanceof ParameterizedType parameterizedValueType) {
            Type rawType = parameterizedValueType.getRawType();
            if (rawType instanceof Class<?> rawClass && List.class.isAssignableFrom(rawClass)) {
                isMultiMap = true;
                valueType = (Class<Object>) parameterizedValueType.getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + valueTypeRaw);
            }
        } else if (valueTypeRaw instanceof Class<?> valueClass) {
            valueType = (Class<Object>) valueClass;
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + valueTypeRaw);
        }

        if (isMultiMap) {
            // It’s a Map<K, List<V>> or Map<K, Set<V>>, use MultiMapTypeResolver
            relationshipHandler.handleMultiMap(field, instance, id, keyType, valueType);
        } else {
            // It’s a normal Map<K, V>
            relationshipHandler.handleNormalMap(field, instance, id, keyType, valueType);
        }
    }

    private void handleOneToManyField(FieldData<?> field, T instance, ID primaryId) {
        Class<?> childType = field.oneToMany().mappedBy();
        RepositoryInformation childInfo = RepositoryMetadata.getMetadata(childType);
        Objects.requireNonNull(childInfo, "Missing metadata for " + childType);

        FieldData<?> childPK = childInfo.getPrimaryKey();
        Class<?> pkType = resolveChildKeyType(childInfo, childPK);

        SQLValueTypeResolver<ID> resolver = (SQLValueTypeResolver<ID>) ValueTypeResolverRegistry.INSTANCE.getResolver(pkType);
        relationshipHandler.handleOneToManyRelationship(field, instance, primaryId, resolver, childInfo);
    }

    private Class<?> resolveChildKeyType(RepositoryInformation childInfo, FieldData<?> childPK) {
        if (childPK != null) return childPK.type();

        return childInfo.getManyToOneCache().values().stream()
                .filter(f -> f.type() == repoInfo.getType())
                .findFirst()
                .map(f -> repoInfo.getPrimaryKey().type())
                .orElseThrow(() -> new IllegalArgumentException("No PK found for " + childInfo.getType()));
    }

    private ID resolvePrimaryKey(ResultSet rs) throws Exception {
        FieldData<?> pkField = repoInfo.getPrimaryKey();
        SQLValueTypeResolver<ID> resolver = (SQLValueTypeResolver<ID>) ValueTypeResolverRegistry.INSTANCE.getResolver(pkField.type());
        return resolver.resolve(rs, pkField.name());
    }

    static void populateFieldInternal(ResultSet rs, FieldData<?> field, Object instance) throws Exception {
        Object value = resolveFieldValue(rs, field);
        if (value != null) field.setValue(instance, value);
    }

    private static Object resolveFieldValue(ResultSet rs, FieldData<?> field) throws Exception {
        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(field.type());
        return resolver.resolve(rs, field.name());
    }

    private Object resolveInsertValue(FieldData<?> field, T entity) {
        if (field.now()) {
            Supplier<Object> nowSupplier = NOW_MAPPERS.get(field.type());
            if (nowSupplier == null) throw new IllegalArgumentException("Unsupported @Now type: " + field.type());
            return nowSupplier.get();
        }

        Object rawValue = field.getValue(entity);
        return rawValue != null ? rawValue : field.defaultValue();
    }

    private static SQLValueTypeResolver<Object> getValueResolver(FieldData<?> field) {
        RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(field.type());

        if (relatedInfo != null) {
            FieldData<?> pkField = relatedInfo.getPrimaryKey();
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>)
                    ValueTypeResolverRegistry.INSTANCE.getResolver(pkField.type());

            return new SQLValueTypeResolver<>() {
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
            };
        }

        return (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(field.type());
    }
}
