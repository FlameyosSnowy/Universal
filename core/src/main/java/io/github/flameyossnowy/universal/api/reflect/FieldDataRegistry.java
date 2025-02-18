package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.defvalues.DefaultTypeProvider;
import io.github.flameyossnowy.universal.api.annotations.*;
import me.sunlan.fastreflection.FastField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public class FieldDataRegistry {
    private final Class<?> entityClass;
    private FieldData<?> primaryKey;
    private Class<?>[] types;
    private final Map<String, FieldData<?>> fieldDataMap = new LinkedHashMap<>();

    public FieldDataRegistry(final Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public <T> FieldData<T> createFieldData(@NotNull Field field, String tableName) {
        field.setAccessible(true);
        DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
        DefaultValueProvider defaultValueProvider = field.getAnnotation(DefaultValueProvider.class);
        
        FastField fastField = FastField.create(field, true);

        return new FieldData<>(field.getName(), tableName, fastField, field, (Class<T>) field.getType(),
                field.isAnnotationPresent(Id.class),
                field.isAnnotationPresent(AutoIncrement.class),
                field.isAnnotationPresent(NonNull.class),
                field.isAnnotationPresent(Unique.class),
                field.getAnnotation(Constraint.class),
                field.getAnnotation(References.class),
                field.getAnnotation(Condition.class),
                field.getAnnotation(OnUpdate.class),
                field.getAnnotation(OnDelete.class),
                field.getAnnotation(Foreign.class),
                field.getAnnotation(Cast.class),
                field.getAnnotation(ManyToOne.class),
                field.getAnnotation(OneToMany.class),
                field.getAnnotation(Join.class),
                resolveDefaultValue(defaultValue, defaultValueProvider)
        );
    }

    private @Nullable Object resolveDefaultValue(DefaultValue defaultValue, DefaultValueProvider provider) {
        if (defaultValue != null) return defaultValue.value();
        if (provider == null) return null;

        try {
            Class<?> clazz = provider.value();
            Object instance = ReflectiveMetaData.newInstance(clazz);
            if (!(instance instanceof DefaultTypeProvider<?> typeProvider)) {
                throw new IllegalArgumentException("Class " + clazz.getSimpleName() + " does not implement DefaultTypeProvider");
            }
            return typeProvider.supply();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void processFields(String tableName) {
        Field[] fields = entityClass.getDeclaredFields();
        Class<?>[] types = new Class<?>[fields.length];

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (Modifier.isStatic(field.getModifiers())) continue;

            FieldData<?> fieldData = this.createFieldData(field, tableName);
            fieldDataMap.put(field.getName(), fieldData);
            types[i] = fieldData.type();

            if (fieldData.primary() && primaryKey != null) {
                throw new IllegalArgumentException("Class " + entityClass.getName() + " has multiple primary keys");
            }

            if (fieldData.primary()) {
                primaryKey = fieldData;
            }
        }

        this.types = types;
    }

    public Map<String, FieldData<?>> getFieldDataMap() {
        return fieldDataMap;
    }

    public FieldData<?> getPrimaryKey() {
        return primaryKey;
    }

    public Class<?>[] getTypes() {
        return types;
    }
}