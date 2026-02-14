package io.github.flameyossnowy.universal.api.factory;

import java.util.Collection;
import java.util.Map;

public interface RelationshipLoader<T, ID> {

    <E> E oneToOne(
        String fieldName,
        ID id,
        Class<E> elementType
    );

    <E> E manyToOne(
        String fieldName,
        ID id,
        Class<E> elementType
    );

    <E> Collection<E> oneToMany(
        String fieldName,
        ID id,
        Class<E> elementType
    );

    <E> Collection<E> loadList(
        String fieldName,
        ID id,
        Class<E> elementType
    );

    <E> Collection<E> loadSet(
        String fieldName,
        ID id,
        Class<E> elementType
    );

    <E, K> Map<E, K> loadMap(
        String fieldName,
        ID id,
        Class<E> keyType,
        Class<K> valueType
    );

    <E> E[] loadArray(
        String fieldName,
        ID id,
        Class<E> componentType
    );
}