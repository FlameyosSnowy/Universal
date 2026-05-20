package io.github.flameyossnowy.universal.checker.schema;

import io.github.flameyossnowy.universal.checker.CollectionKind;

public record FieldSchema(
    String name,
    String type,
    boolean nullable,
    boolean indexed,
    boolean relationship,
    CollectionKind collectionKind,
    FieldType fieldType
) {}