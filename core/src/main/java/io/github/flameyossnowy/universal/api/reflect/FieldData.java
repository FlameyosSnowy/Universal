package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;

import io.github.flameyossnowy.universal.api.utils.Logging;

import me.sunlan.fastreflection.FastField;
import me.sunlan.fastreflection.FastMethod;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Objects;

@ApiStatus.Internal
@SuppressWarnings({"unchecked", "unused"})
public class FieldData<T> {
    public static final Type[] TYPES = new Type[0];
    private RepositoryInformation declaringInformation;
    private boolean indexed;
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
    private final ExternalRepository externalRepository;
    private final Object defaultValue;

    public FieldData(RepositoryInformation declaringInformation, String name, String fieldName, String tableName,
                     FastField field, Field rawField, Class<T> type, boolean primary, boolean autoIncrement,
                     boolean nonNull, boolean unique, boolean now, Constraint constraint, Condition condition,
                     OnUpdate onUpdate, OnDelete onDelete, OneToMany oneToMany, ManyToOne manyToOne,
                     OneToOne oneToOne, ExternalRepository externalRepository,
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
        this.externalRepository = externalRepository;
    }

    public FieldData(RepositoryInformation declaringInformation, String name, String fieldName, String tableName, RecordComponent rawField, Class<T> type, boolean primary, boolean autoIncrement,
                     boolean nonNull, boolean unique, boolean now, Constraint constraint, Condition condition,
                     OnUpdate onUpdate, OnDelete onDelete, OneToMany oneToMany, ManyToOne manyToOne,
                     OneToOne oneToOne, ExternalRepository externalRepository,
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
        this.externalRepository = externalRepository;
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

    public ExternalRepository externalRepository() {
        return externalRepository;
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
                ", externalRepository=" + (externalRepository != null) +
                ", defaultValue=" + defaultValue +
                ", indexed=" + indexed +
                '}';
    }

    public boolean isRelationship() {
        return oneToMany != null || manyToOne != null || oneToOne != null || externalRepository != null;
    }

    public boolean isExternalRelationship() {
        return externalRepository() != null;
    }

    public <E extends Annotation> E getAnnotation(Class<E> annotationClass) {
        return rawComponentField != null ? rawComponentField.getAnnotation(annotationClass) : rawField.getAnnotation(annotationClass);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof FieldData<?> fieldData)) return false;

        return primary == fieldData.primary
                && autoIncrement == fieldData.autoIncrement
                && nonNull == fieldData.nonNull
                && unique == fieldData.unique
                && now == fieldData.now
                && declaringInformation.equals(fieldData.declaringInformation)
                && name.equals(fieldData.name)
                && tableName.equals(fieldData.tableName)
                && indexed == fieldData.indexed
                && Objects.equals(field, fieldData.field)
                && Objects.equals(rawField, fieldData.rawField)
                && Objects.equals(rawComponentField, fieldData.rawComponentField)
                && Objects.equals(componentFieldGetter, fieldData.componentFieldGetter)
                && type.equals(fieldData.type)
                && Objects.equals(constraint, fieldData.constraint)
                && Objects.equals(condition, fieldData.condition)
                && Objects.equals(onUpdate, fieldData.onUpdate)
                && Objects.equals(onDelete, fieldData.onDelete)
                && Objects.equals(oneToMany, fieldData.oneToMany)
                && Objects.equals(manyToOne, fieldData.manyToOne)
                && Objects.equals(oneToOne, fieldData.oneToOne)
                && Objects.equals(externalRepository, fieldData.externalRepository)
                && Objects.equals(defaultValue, fieldData.defaultValue);
    }

    @Override
    public int hashCode() {
        int result = declaringInformation.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + tableName.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + Boolean.hashCode(primary);
        result = 31 * result + Boolean.hashCode(autoIncrement);
        result = 31 * result + Boolean.hashCode(nonNull);
        result = 31 * result + Boolean.hashCode(unique);
        result = 31 * result + Boolean.hashCode(now);
        result = 31 * result + Objects.hashCode(field);
        result = 31 * result + Objects.hashCode(rawField);
        result = 31 * result + Objects.hashCode(rawComponentField);
        result = 31 * result + Objects.hashCode(componentFieldGetter);
        result = 31 * result + Objects.hashCode(constraint);
        result = 31 * result + Objects.hashCode(condition);
        result = 31 * result + Objects.hashCode(onUpdate);
        result = 31 * result + Objects.hashCode(onDelete);
        result = 31 * result + Objects.hashCode(oneToMany);
        result = 31 * result + Objects.hashCode(manyToOne);
        result = 31 * result + Objects.hashCode(oneToOne);
        result = 31 * result + Objects.hashCode(externalRepository);
        result = 31 * result + Objects.hashCode(defaultValue);
        result = 31 * result + Boolean.hashCode(indexed);
        return result;
    }

    public boolean notIndexed() {
        return !indexed;
    }

    public boolean indexed() {
        return indexed;
    }

    void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public Type[] getGenericTypes() {
        if (rawField != null) {
            Type genericType = rawField.getGenericType();
            if (genericType instanceof ParameterizedType parameterizedType) {
                return parameterizedType.getActualTypeArguments();
            }
        }

        if (rawComponentField != null) {
            Type genericType = rawComponentField.getGenericType();
            if (genericType instanceof ParameterizedType parameterizedType) {
                return parameterizedType.getActualTypeArguments();
            }
        }
        return TYPES;
    }
}
