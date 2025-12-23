package io.github.flameyossnowy.universal.api.operation;

/**
 * Enum representing the types of operations that can be performed on a repository.
 */
public enum OperationType {
    /**
     * Read operation (SELECT, GET, FIND)
     */
    READ,

    /**
     * Write operation (INSERT, CREATE)
     */
    WRITE,

    /**
     * Update operation (UPDATE, PUT)
     */
    UPDATE,

    /**
     * Delete operation (DELETE, REMOVE)
     */
    DELETE,

    /**
     * Schema operation (CREATE TABLE, DROP TABLE, CREATE INDEX)
     */
    SCHEMA,

    /**
     * Transaction operation (BEGIN, COMMIT, ROLLBACK)
     */
    TRANSACTION,

    /**
     * Custom operation (user-defined)
     */
    CUSTOM,

    /**
     * Remote operation (microservice call, HTTP request)
     */
    REMOTE
}
