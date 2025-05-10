package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.reflect.*;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.resolvers.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.github.flameyossnowy.universal.sql.internals.DatabaseRelationshipHandler.getOneToOneField;

@ApiStatus.Internal
@SuppressWarnings("unchecked")
public class ObjectFactory<T, ID> implements SQLObjectFactory<T, ID> {
    protected final RepositoryInformation repoInfo;
    protected final DatabaseRelationshipHandler<T, ID> relationshipHandler;
    protected final SQLConnectionProvider connectionProvider;
    protected final Class<ID> idClass;
    protected final boolean hasPrimaryKey;

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

    protected static final Map<Class<?>, Supplier<Object>> NOW_MAPPERS = Map.ofEntries(
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
            if (isRelationshipField(field)) {
                continue;
            }

            if (isListField(field)) {
                args[index] = handleGenericListField(field, id, rs);
                index++;
                continue;
            }

            if (isSetField(field)) {
                args[index] = handleGenericSetField(field, id, rs);
                index++;
                continue;
            }

            if (isMapField(field)) {
                MapData result = getMapData(field);

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
            if (isRelationshipField(field)) {
                continue;
            }

            if (isListField(field)) {
                field.setValue(instance, handleGenericListField(field, id, rs));
                continue;
            }

            if (isSetField(field)) {
                field.setValue(instance, handleGenericSetField(field, id, rs));
                continue;
            }

            if (isMapField(field)) {
                MapData result = getMapData(field);

                if (result.isMultiMap()) {
                    field.setValue(instance, relationshipHandler.handleMultiMap(id, result.keyType(), result.valueType()));
                    continue;
                }
                field.setValue(instance, relationshipHandler.handleNormalMap(id, result.keyType(), result.valueType()));
                continue;
            }

            if (field.type().isArray()) {
                field.setValue(instance, handleGenericArrayField(field, id, rs));
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

    @Contract("_ -> new")
    private static @NotNull MapData getMapData(@NotNull FieldData<?> field) {
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
        return new MapData(keyType, valueType, isMultiMap);
    }

    record MapData(Class<Object> keyType, Class<Object> valueType, boolean isMultiMap) {}

    @Override
    public @NotNull T createWithRelationships(ResultSet rs) throws Exception {
        return populateRelationshipInstanceWithPojo(rs);
    }

    private @NotNull T populateRelationshipInstanceWithPojo(ResultSet rs) throws Exception {
        T instance = (T) repoInfo.newInstance();
        ID primaryId = hasPrimaryKey ? resolvePrimaryKey(rs) : null;

        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.primary()) {
                field.setValue(instance, primaryId);
            } else if (field.oneToMany() != null && hasPrimaryKey) {
                field.setValue(instance, handleOneToManyField(field, primaryId));
            } else if (field.oneToOne() != null && hasPrimaryKey) {
                Object related = relationshipHandler.handleOneToOneRelationship(rs, field);

                OneToOneField backReference = getOneToOneField(
                        Objects.requireNonNull(RepositoryMetadata.getMetadata(field.type())),
                        repoInfo
                );

                backReference.foundRelatedField().setValue(related, instance);
                field.setValue(instance, related);
            } else if (field.manyToOne() != null) {
                field.setValue(instance, DatabaseRelationshipHandler.handleManyToOneRelationship(rs, field));
            } else if (isListField(field) && hasPrimaryKey) {
                field.setValue(instance, handleGenericListField(field, primaryId, rs));
            } else if (isSetField(field) && hasPrimaryKey) {
                field.setValue(instance, handleGenericSetField(field, primaryId, rs));
            } else if (field.type().isArray()) {
                field.setValue(instance, handleGenericArrayField(field, primaryId, rs));
            } else if (isMapField(field) && hasPrimaryKey) {
                MapData result = getMapData(field);

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
    public void insertEntity(PreparedStatement stmt, T entity) throws Exception {
        int paramIndex = 1;

        // First pass: regular fields
        for (FieldData<?> field : repoInfo.getFields()) {
            if (field.autoIncrement()) continue;
            if (isListField(field) || isMapField(field) || isSetField(field)) continue; // skip collections for now

            Object valueToInsert = resolveInsertValue(field, entity);
            SQLValueTypeResolver<Object> resolver = getValueResolver(field);

            Logging.deepInfo("Binding parameter " + paramIndex + ": " + valueToInsert);
            resolver.insert(stmt, paramIndex++, valueToInsert);
        }
    }

    @Override
    public void insertCollectionEntities(T entity, ID id, PreparedStatement statement) throws Exception {
        for (FieldData<?> field : repoInfo.getFields()) {
            if ((isListField(field) || isSetField(field)) && !field.isRelationship()) {
                handleLists(entity, id, field, statement);
            } else if (isMapField(field)) {
                handleMaps(entity, id, field);
            }
        }
    }

    protected void handleMaps(T entity, ID id, FieldData<?> field) throws Exception {
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


    protected void handleLists(T entity, ID id, FieldData<?> field, PreparedStatement statement) throws Exception {
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

    protected static boolean isRelationshipField(FieldData<?> field) {
        return field.oneToOne() != null || field.oneToMany() != null || field.manyToOne() != null;
    }

    protected static boolean isListField(FieldData<?> field) {
        return List.class.isAssignableFrom(field.type());
    }

    protected static boolean isSetField(FieldData<?> field) {
        return Set.class.isAssignableFrom(field.type());
    }

    protected static boolean isMapField(FieldData<?> field) {
        return Map.class.isAssignableFrom(field.type());
    }

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
        Class<?> childType = field.oneToMany().mappedBy();
        RepositoryInformation childInfo = RepositoryMetadata.getMetadata(childType);
        Objects.requireNonNull(childInfo, "Missing metadata for " + childType);

        FieldData<?> childPK = childInfo.getPrimaryKey();
        Class<?> pkType = resolveChildKeyType(childInfo, childPK);

        SQLValueTypeResolver<ID> resolver = (SQLValueTypeResolver<ID>) ValueTypeResolverRegistry.INSTANCE.getResolver(pkType);
        return relationshipHandler.handleOneToManyRelationship(field, primaryId, resolver, childInfo);
    }

    protected Class<?> resolveChildKeyType(RepositoryInformation childInfo, FieldData<?> childPK) {
        if (childPK != null) return childPK.type();

        return childInfo.getManyToOneCache().values().stream()
                .filter(f -> f.type() == repoInfo.getType())
                .findFirst()
                .map(f -> repoInfo.getPrimaryKey().type())
                .orElseThrow(() -> new IllegalArgumentException("No primary key found for " + childInfo.getType()));
    }

    protected ID resolvePrimaryKey(ResultSet rs) throws Exception {
        FieldData<?> pkField = repoInfo.getPrimaryKey();
        SQLValueTypeResolver<ID> resolver = (SQLValueTypeResolver<ID>) ValueTypeResolverRegistry.INSTANCE.getResolver(pkField.type());
        return resolver.resolve(rs, pkField.name());
    }

    static void populateFieldInternal(ResultSet rs, FieldData<?> field, Object instance) throws Exception {
        Object value = resolveFieldValue(rs, field);
        if (value != null) field.setValue(instance, value);
    }

    protected static Object resolveFieldValue(ResultSet rs, FieldData<?> field) throws Exception {
        SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(field.type());
        return resolver.resolve(rs, field.name());
    }

    protected Object resolveInsertValue(FieldData<?> field, T entity) {
        if (field.now()) {
            Supplier<Object> nowSupplier = NOW_MAPPERS.get(field.type());
            if (nowSupplier == null) throw new IllegalArgumentException("Unsupported @Now type: " + field.type());
            return nowSupplier.get();
        }

        Object rawValue = field.getValue(entity);
        return rawValue != null ? rawValue : field.defaultValue();
    }

    protected static SQLValueTypeResolver<Object> getValueResolver(FieldData<?> field) {
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
