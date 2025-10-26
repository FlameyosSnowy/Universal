package io.github.flameyossnowy.universal.sql;

/**
 * The database implementation interface, used to determine if the database supports certain features, and to retrieve database-specific keywords.
 */
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public interface DatabaseImplementation {
    /**
     * Retrieves the name of the database implementation.
     *
     * @return the name of the database implementation
     */
    String getName();

    /**
     * Determines if the database supports arrays.
     * <p>
     * It does not mean that arrays cannot be stored in the database, but it means if this method returns false, then arrays cannot natively be stored in the database.
     * <p>
     * Such as in PostgreSQL:
     * <pre>
     *     CREATE TABLE test (
     *          id BIGINT GENERATED ALWAYS AS IDENTITY,
     *          name TEXT[],
     *          PRIMARY KEY (id)
*    *     );
     * </pre>
     * Here, the database clearly stores the array natively, this is when this method should return true.
     *
     * @return true if the database supports arrays
     */
    boolean supportsArrays();

    /**
     * Retrieves the keyword for auto-incrementing fields.
     * <p>
     * For example, in MySQL it would be "AUTO_INCREMENT", in PostgreSQL it would be "GENERATED ALWAYS AS IDENTITY", in SQLite it would be "AUTOINCREMENT", etc.
     *
     * @return the keyword for auto-incrementing fields
     */
    String autoIncrementKeyword();
}
