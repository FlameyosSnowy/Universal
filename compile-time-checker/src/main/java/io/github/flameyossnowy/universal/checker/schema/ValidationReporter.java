package io.github.flameyossnowy.universal.checker.schema;

/**
 * Interface for reporting validation errors and warnings.
 * Implemented by annotation processor to emit compiler diagnostics.
 */
public interface ValidationReporter {

    /**
     * Reports an error at the specified field.
     *
     * @param field the field where the error occurred
     * @param message the error message
     */
    void error(FieldSchema field, String message);

    /**
     * Reports a warning at the specified field.
     *
     * @param field the field where the warning occurred
     * @param message the warning message
     */
    void warn(FieldSchema field, String message);

    /**
     * Reports a general error (not tied to a specific field).
     *
     * @param message the error message
     */
    void error(String message);

    /**
     * Reports a general warning (not tied to a specific field).
     *
     * @param message the warning message
     */
    void warn(String message);
}
