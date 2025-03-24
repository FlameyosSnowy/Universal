package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;

import io.github.flameyossnowy.universal.api.utils.Logging;
import me.sunlan.fastreflection.FastField;

import java.lang.reflect.Field;

@SuppressWarnings({"unchecked", "unused"})
public class FieldData<T> {
    private RepositoryInformation declaringInformation;
    private final String name;
    private final String tableName;
    private final FastField field;
    private final Field rawField;
    private final Class<T> type;
    private final boolean primary;
    private final boolean autoIncrement;
    private final boolean nonNull;
    private final boolean unique;
    private final Constraint constraint;
    private final Condition condition;
    private final OnUpdate onUpdate;
    private final OnDelete onDelete;
    private final OneToMany oneToMany;
    private final ManyToOne manyToOne;
    private final Object defaultValue;

    public FieldData(RepositoryInformation declaringInformation, String name, String fieldName, String tableName,
                     FastField field, Field rawField, Class<T> type, boolean primary, boolean autoIncrement,
                     boolean nonNull, boolean unique, Constraint constraint, Condition condition,
                     OnUpdate onUpdate, OnDelete onDelete, OneToMany oneToMany, ManyToOne manyToOne,
                     Object defaultValue) {
        this.declaringInformation = declaringInformation;
        this.name = name;
        this.tableName = tableName;
        this.field = field;
        this.rawField = rawField;
        this.type = type;
        this.primary = primary;
        this.autoIncrement = autoIncrement;
        this.nonNull = nonNull;
        this.unique = unique;
        this.constraint = constraint;
        this.condition = condition;
        this.onUpdate = onUpdate;
        this.onDelete = onDelete;
        this.oneToMany = oneToMany;
        this.manyToOne = manyToOne;
        this.defaultValue = defaultValue;
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

    public <E> E getValue(Object obj) {
        try {
            Logging.deepInfo("Retrieving value from field: " + field.getName() + " of type: " + rawField.getType().getSimpleName() + " of object: " + declaringInformation.getType().getSimpleName());
            return (E) field.get(obj);
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
                ", condition=" + condition +
                ", onUpdate=" + onUpdate +
                ", onDelete=" + onDelete +
                ", oneToMany=" + oneToMany +
                ", manyToOne=" + manyToOne +
                ", defaultValue=" + defaultValue +
                '}';
    }
}
