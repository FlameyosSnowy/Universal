package io.github.flameyossnowy.universal.api.reflect;

import me.sunlan.fastreflection.FastConstructor;
import me.sunlan.fastreflection.FastField;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class ReflectiveMetaData {
    private static final Map<Class<?>, FastConstructor<?>> CACHE = new ConcurrentHashMap<>();

    /**
    * Gets a value from an object's field dynamically.
    *
    * @param obj   The object whose field value is to be retrieved.
    * @param field The field to retrieve.
    * @return The field's value.
    */
    public static <T, E> E getFieldValue(T obj, FieldData<?> field) {
        try {
            return (E) field.field().get(obj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
    * Sets a value to an object's field dynamically.
    *
    * @param obj   The object whose field value is to be set.
    * @param field The field to set.
    * @param value The value to set.
    */
    public static <T, E> void setFieldValue(T obj, E value, FieldData<?> field) {
        try {
            field.field().set(obj, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private ReflectiveMetaData() {}

    public static @NotNull Object newInstance(RepositoryInformation metadata) {
        return newInstance(metadata.type());
    }

    public static @NotNull Object newInstance(Class<?> data) {
        try {
            FastConstructor<?> constructor = CACHE.computeIfAbsent(data, key -> {
                try {
                    return FastConstructor.create(data.getDeclaredConstructor(), true);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
            return constructor.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
