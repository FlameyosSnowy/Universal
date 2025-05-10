package io.github.flameyossnowy.universal.api.annotations.enums;

/**
 * The type of index for the database, normal, unique, or partial, partial doesn't exist in MongoDB.
 */
public enum IndexType {
    NORMAL,
    UNIQUE,
    PARTIAL
}
