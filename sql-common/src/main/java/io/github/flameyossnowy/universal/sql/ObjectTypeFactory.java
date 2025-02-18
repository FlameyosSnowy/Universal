package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.BiFunction;

public class ObjectTypeFactory {
    public static <T, D> @NotNull T create(Class<T> type, D data, BiFunction<D, FieldData<?>, Object> converter) {
        T instance = (T) ReflectiveMetaData.newInstance(type);

        try {
            buildFields(data, instance, converter);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return instance;
    }

    private static <T, D> void buildFields(final D set, final @NotNull T instance, BiFunction<D, FieldData<?>, Object> converter) throws Throwable {
        RepositoryInformation information = RepositoryMetadata.getMetadata(instance.getClass());
        Collection<FieldData<?>> data = information.fields();
        for (FieldData<?> entry : data) {
            Object value = converter.apply(set, entry);
            ReflectiveMetaData.setFieldValue(instance, value, entry);
        }
    }
}
