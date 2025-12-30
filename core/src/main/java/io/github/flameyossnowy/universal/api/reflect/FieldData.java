package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.Binary;
import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.ExternalRepository;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;

import io.github.flameyossnowy.universal.api.resolver.ResolveWith;
import me.sunlan.fastreflection.FastField;
import me.sunlan.fastreflection.FastMethod;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

@ApiStatus.Internal
@SuppressWarnings({"unchecked", "unused"})
public class FieldData<T> {
    private final RepositoryInformation declaringInformation;
    private boolean indexed;

    private final String name;
    private final String tableName;

    private final FastField field;
    private final Field rawField;

    private final RecordComponent rawComponentField;
    private final FastMethod componentFieldGetter;

    private final Class<T> type;

    private final Class<?> elementType;        // List / Set
    private final Class<?> mapKeyType;
    private final Class<?> mapValueType;
    private final Class<?> arrayComponentType;

    private final boolean primary;
    private final boolean autoIncrement;
    private final boolean nonNull;
    private final boolean unique;
    private final boolean now;

    private final Constraint constraint;
    private final Condition condition;
    private final OnUpdate onUpdate;
    private final OnDelete onDelete;

    private final OneToMany oneToMany;
    private final ManyToOne manyToOne;
    private final OneToOne oneToOne;
    private final ResolveWith resolveWith;
    private final ExternalRepository externalRepository;

    private final Binary binary;
    private final Object defaultValue;

    public FieldData(
        RepositoryInformation declaringInformation,
        String name,
        String fieldName,
        String tableName,
        FastField field,
        Field rawField,
        Class<T> type,
        boolean primary,
        boolean autoIncrement,
        boolean nonNull,
        boolean unique,
        boolean now,
        Constraint constraint,
        Condition condition,
        OnUpdate onUpdate,
        OnDelete onDelete,
        OneToMany oneToMany,
        ManyToOne manyToOne,
        OneToOne oneToOne,
        ExternalRepository externalRepository,
        Object defaultValue,
        Binary binary,
        ResolveWith resolveWith
    ) {
        this.declaringInformation = declaringInformation;
        this.name = name;
        this.tableName = tableName;

        this.field = field;
        this.rawField = rawField;
        this.rawComponentField = null;
        this.componentFieldGetter = null;

        this.type = type;

        GenericInfo info = GenericInfo.resolve(rawField, type);

        this.elementType = info.elementType;
        this.mapKeyType = info.mapKeyType;
        this.mapValueType = info.mapValueType;
        this.arrayComponentType = info.arrayComponentType;

        this.primary = primary;
        this.autoIncrement = autoIncrement;
        this.nonNull = nonNull;
        this.unique = unique;
        this.now = now;

        this.constraint = constraint;
        this.condition = condition;
        this.onUpdate = onUpdate;
        this.onDelete = onDelete;

        this.oneToMany = oneToMany;
        this.manyToOne = manyToOne;
        this.oneToOne = oneToOne;
        this.externalRepository = externalRepository;

        this.defaultValue = defaultValue;
        this.binary = binary;
        this.resolveWith = resolveWith;
    }

    public FieldData(
        RepositoryInformation declaringInformation,
        String name,
        String fieldName,
        String tableName,
        RecordComponent rawComponentField,
        Class<T> type,
        boolean primary,
        boolean autoIncrement,
        boolean nonNull,
        boolean unique,
        boolean now,
        Constraint constraint,
        Condition condition,
        OnUpdate onUpdate,
        OnDelete onDelete,
        OneToMany oneToMany,
        ManyToOne manyToOne,
        OneToOne oneToOne,
        ExternalRepository externalRepository,
        Object defaultValue,
        Binary binary,
        ResolveWith resolveWith
    ) {
        this.declaringInformation = declaringInformation;
        this.name = name;
        this.tableName = tableName;

        this.field = null;
        this.rawField = null;
        this.rawComponentField = rawComponentField;
        this.componentFieldGetter = FastMethod.create(rawComponentField.getAccessor());

        this.type = type;

        // === resolve generics ONCE ===
        GenericInfo info = GenericInfo.resolve(rawComponentField, type);
        this.elementType = info.elementType;
        this.mapKeyType = info.mapKeyType;
        this.mapValueType = info.mapValueType;
        this.arrayComponentType = info.arrayComponentType;

        this.primary = primary;
        this.autoIncrement = autoIncrement;
        this.nonNull = nonNull;
        this.unique = unique;
        this.now = now;

        this.constraint = constraint;
        this.condition = condition;
        this.onUpdate = onUpdate;
        this.onDelete = onDelete;

        this.oneToMany = oneToMany;
        this.manyToOne = manyToOne;
        this.oneToOne = oneToOne;
        this.externalRepository = externalRepository;

        this.defaultValue = defaultValue;
        this.binary = binary;
        this.resolveWith = resolveWith;
    }

    public ResolveWith resolveWith() {
        return resolveWith;
    }

    public boolean isRelationship() {
        return oneToMany != null || manyToOne != null || oneToOne != null || externalRepository != null;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public RepositoryInformation declaringInformation() {
        return declaringInformation;
    }

    public boolean indexed() {
        return indexed;
    }

    public boolean notIndexed() {
        return indexed;
    }

    public String name() {
        return name;
    }

    public String tableName() {
        return tableName;
    }

    public boolean unique() {
        return unique;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public boolean binary() {
        return binary != null;
    }

    public ExternalRepository externalRepository() {
        return externalRepository;
    }

    public OneToOne oneToOne() {
        return oneToOne;
    }

    public ManyToOne manyToOne() {
        return manyToOne;
    }

    public OneToMany oneToMany() {
        return oneToMany;
    }

    public OnDelete onDelete() {
        return onDelete;
    }

    public OnUpdate onUpdate() {
        return onUpdate;
    }

    public Condition condition() {
        return condition;
    }

    public Constraint constraint() {
        return constraint;
    }

    public boolean now() {
        return now;
    }

    public boolean nonNull() {
        return nonNull;
    }

    public boolean autoIncrement() {
        return autoIncrement;
    }

    public boolean primary() {
        return primary;
    }

    public Class<T> type() {
        return type;
    }

    public Class<?> elementType() {
        return elementType;
    }

    public Class<?> mapKeyType() {
        return mapKeyType;
    }

    public Class<?> mapValueType() {
        return mapValueType;
    }

    public Class<?> arrayComponentType() {
        return arrayComponentType;
    }

    public <E> E getValue(Object obj) {
        try {
            return componentFieldGetter != null
                ? (E) componentFieldGetter.invoke(obj)
                : (E) field.get(obj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public <E> void setValue(Object obj, E value) {
        try {
            field.set(obj, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends Annotation> T getAnnotation(Class<T> clazz) {
        return rawField.getAnnotation(clazz);
    }

    private record GenericInfo(Class<?> elementType, Class<?> mapKeyType, Class<?> mapValueType,
                               Class<?> arrayComponentType) {

        static GenericInfo resolve(Field field, Class<?> rawType) {
                return resolve(field.getGenericType(), rawType, field.toString());
            }

            static GenericInfo resolve(RecordComponent component, Class<?> rawType) {
                return resolve(component.getGenericType(), rawType, component.toString());
            }

            private static GenericInfo resolve(Type genericType, Class<?> rawType, String source) {
                Class<?> element = null;
                Class<?> key = null;
                Class<?> value = null;
                Class<?> array = rawType.isArray() ? rawType.getComponentType() : null;

                if (genericType instanceof ParameterizedType pt) {
                    Type[] args = pt.getActualTypeArguments();

                    if (Collection.class.isAssignableFrom(rawType)) {
                        element = requireClass(args[0], source);
                    } else if (Map.class.isAssignableFrom(rawType)) {
                        key = requireClass(args[0], source);
                        value = requireClass(args[1], source);
                    }
                }

                return new GenericInfo(element, key, value, array);
            }

            private static Class<?> requireClass(Type type, String source) {
                if (type instanceof Class<?> cls) return cls;
                throw new IllegalStateException(
                    "Unsupported generic type in " + source + ": " + type
                );
            }
        }
}
