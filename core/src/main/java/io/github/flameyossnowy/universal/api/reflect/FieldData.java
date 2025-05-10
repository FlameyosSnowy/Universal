package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;

import io.github.flameyossnowy.universal.api.utils.Logging;

import me.sunlan.fastreflection.FastField;
import me.sunlan.fastreflection.FastMethod;
import org.jetbrains.annotations.ApiStatus;
import sun.misc.Unsafe;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;

@ApiStatus.Internal
@SuppressWarnings({"unchecked", "unused"})
public class FieldData<T> {
    private RepositoryInformation declaringInformation;
    private final String name;
    private final String tableName;
    private final FastField field;

    private final Field rawField;
    private final RecordComponent rawComponentField;
    private final FastMethod componentFieldGetter;



    private final Class<T> type;
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
    private final Object defaultValue;

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

    public FieldData(RepositoryInformation declaringInformation, String name, String fieldName, String tableName,
                     FastField field, Field rawField, Class<T> type, boolean primary, boolean autoIncrement,
                     boolean nonNull, boolean unique, boolean now, Constraint constraint, Condition condition,
                     OnUpdate onUpdate, OnDelete onDelete, OneToMany oneToMany, ManyToOne manyToOne,
                     OneToOne oneToOne,
                     Object defaultValue) {
        this.declaringInformation = declaringInformation;
        this.name = name;
        this.tableName = tableName;

        this.field = field;
        this.rawField = rawField;

        this.componentFieldGetter = null;
        this.rawComponentField = null;

        this.type = type;
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
        this.defaultValue = defaultValue;
        this.oneToOne = oneToOne;
    }

    public FieldData(RepositoryInformation declaringInformation, String name, String fieldName, String tableName, RecordComponent rawField, Class<T> type, boolean primary, boolean autoIncrement,
                     boolean nonNull, boolean unique, boolean now, Constraint constraint, Condition condition,
                     OnUpdate onUpdate, OnDelete onDelete, OneToMany oneToMany, ManyToOne manyToOne,
                     OneToOne oneToOne,
                     Object defaultValue) {
        this.declaringInformation = declaringInformation;
        this.name = name;
        this.tableName = tableName;
        this.field = null;
        this.rawField = null;
        this.rawComponentField = rawField;
        this.componentFieldGetter = FastMethod.create(rawField.getAccessor());
        this.type = type;
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
        this.defaultValue = defaultValue;
        this.oneToOne = oneToOne;
    }

    public boolean now() {
        return now;
    }

    public RepositoryInformation getDeclaringInformation() {
        return declaringInformation;
    }

    public String name() {
        return name;
    }

    public String tableName() {
        return tableName;
    }

    public FastField field() {
        return field;
    }

    public RecordComponent rawComponentField() {
        return rawComponentField;
    }

    public Field rawField() {
        return rawField;
    }

    public Class<T> type() {
        return type;
    }

    public boolean primary() {
        return primary;
    }

    public boolean autoIncrement() {
        return autoIncrement;
    }

    public boolean nonNull() {
        return nonNull;
    }

    public boolean unique() {
        return unique;
    }

    public Constraint constraint() {
        return constraint;
    }

    public Condition condition() {
        return condition;
    }

    public OnUpdate onUpdate() {
        return onUpdate;
    }

    public OnDelete onDelete() {
        return onDelete;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public void setDeclaringInformation(RepositoryInformation declaringInformation) {
        this.declaringInformation = declaringInformation;
    }

    public OneToMany oneToMany() {
        return oneToMany;
    }

    public ManyToOne manyToOne() {
        return manyToOne;
    }

    public OneToOne oneToOne() {
        return oneToOne;
    }

    public <E> E getValue(Object obj) {
        try {
            Logging.deepInfo("Retrieving value from field: " +
                    (rawComponentField != null ? rawComponentField.getName() : field.getName()) + " of type: " +
                    (rawComponentField != null ? rawComponentField.getType().getSimpleName() : rawField.getType().getSimpleName()) + " of object: " +
                    declaringInformation.getType().getSimpleName());
            return componentFieldGetter != null ? (E) componentFieldGetter.invoke(obj) : (E) field.get(obj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public <E> void setValue(Object obj, E value) {
        try {
            Logging.deepInfo("Setting value to field: " + field.getName() + " of type: " + rawField.getType().getSimpleName() + " of object: " + declaringInformation.getType().getSimpleName() + " with value: " + value + " to " + obj);

            field.set(obj, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void modifyRecordComponent(Object instance, Object related) {
        try {
            long offset = UNSAFE.objectFieldOffset(rawField);
            UNSAFE.putObject(related, offset, instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> void modifyRecordComponent(T instance, OneToOneField backReference, Object related) {
        try {
            long offset = UNSAFE.objectFieldOffset(backReference.foundRelatedField().rawField());
            UNSAFE.putObject(related, offset, instance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "FieldData{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", field=" + field +
                ", rawField=" + rawField +
                ", type=" + type +
                ", primary=" + primary +
                ", autoIncrement=" + autoIncrement +
                ", nonNull=" + nonNull +
                ", unique=" + unique +
                ", constraint=" + constraint +
                ", now=" + now +
                ", condition=" + condition +
                ", onUpdate=" + onUpdate +
                ", onDelete=" + onDelete +
                ", oneToMany=" + (oneToMany != null) +
                ", manyToOne=" + (manyToOne != null) +
                ", oneToOne=" + (oneToOne != null) +
                ", defaultValue=" + defaultValue +
                '}';
    }

    public boolean isRelationship() {
        return oneToMany() != null || manyToOne() != null || oneToOne() != null;
    }

    public <E extends Annotation> E getAnnotation(Class<E> annotationClass) {
        return rawComponentField != null ? rawComponentField.getAnnotation(annotationClass) : rawField.getAnnotation(annotationClass);
    }
}
