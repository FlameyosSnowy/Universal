package io.github.flameyossnowy.universal.api.reflect;

import io.github.flameyossnowy.universal.api.annotations.*;

import me.sunlan.fastreflection.FastField;

import java.lang.reflect.Field;

public record FieldData<T>(
        String name,
        String tableName,
        FastField field,
        Field rawField,
        Class<T> type,
        boolean primary,
        boolean autoIncrement,
        boolean nonNull,
        boolean unique,
        Constraint constraint,
        References references,
        Condition condition,
        OnUpdate onUpdate,
        OnDelete onDelete,
        Foreign foreign,
        Cast cast,
        ManyToOne manyToOne,
        OneToMany oneToMany,
        Join join,
        Object defaultValue
) {}