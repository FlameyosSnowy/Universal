package io.github.flameyossnowy.universal.api.factory;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public interface DatabaseObjectFactory<T, S> {
    Map<Class<?>, Supplier<Object>> NOW_MAPPERS = Map.ofEntries(
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

    /**
     * Creates a new instance of the entity represented by this factory.
     *
     * @param set the data source that the entity should be created from
     * @return a new instance of the entity
     * @throws Exception if an exception occurs while creating the entity
     */
    T create(S set) throws Exception;

    /**
     * Creates a new instance of the entity represented by this factory,
     * resolving all relationships specified by {@link OneToOne},
     * {@link OneToMany}, and {@link ManyToOne} annotations.
     *
     * @param set the data source that the entity should be created from
     * @return a new instance of the entity, with all relationships resolved
     * @throws Exception if an exception occurs while creating the entity
     */
    T createWithRelationships(S set) throws Exception;

    static boolean isListField(FieldData<?> field) {
        return List.class.isAssignableFrom(field.type());
    }

    static boolean isSetField(FieldData<?> field) {
        return Set.class.isAssignableFrom(field.type());
    }

    static boolean isMapField(FieldData<?> field) {
        return Map.class.isAssignableFrom(field.type());
    }

    static boolean isRelationshipField(FieldData<?> field) {
        return field.oneToOne() != null || field.oneToMany() != null || field.manyToOne() != null;
    }

    static <T> Object resolveInsertValue(FieldData<?> field, T entity) {
        if (field.now()) {
            Supplier<Object> nowSupplier = NOW_MAPPERS.get(field.type());
            if (nowSupplier == null) throw new IllegalArgumentException("Unsupported @Now annotated type: " + field.type());
            return nowSupplier.get();
        }

        Object rawValue = field.getValue(entity);
        return rawValue != null ? rawValue : field.defaultValue();
    }

    @Contract("_ -> new")
    static @NotNull MapData getMapData(@NotNull FieldData<?> field) {
        Class<?> keyType = field.mapKeyType();
        Class<?> valueType = field.mapValueType();

        if (keyType == null || valueType == null) {
            throw new IllegalStateException(
                "Field " + field.name() + " is not a Map"
            );
        }

        boolean isMultiMap = false;

        // Detect Map<K, List<V>>
        if (Collection.class.isAssignableFrom(valueType)) {
            Class<?> elementType = field.elementType();

            if (elementType == null) {
                throw new IllegalStateException(
                    "Unsupported Map value type for field " + field.name() +
                        ": Collection without resolvable element type"
                );
            }

            isMultiMap = true;
            valueType = elementType;
        }

        return new MapData(
            cast(keyType),
            cast(valueType),
            isMultiMap
        );
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> cast(Class<?> cls) {
        return (Class<Object>) cls;
    }

    record MapData(Class<Object> keyType, Class<Object> valueType, boolean isMultiMap) {}
}
