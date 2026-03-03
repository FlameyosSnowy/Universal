package io.github.flameyossnowy.universal.api.options.validator;

import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;

/**
 * Validates queries before execution to catch potential issues early.
 *
 * <p>Implementations should be <strong>thread-safe</strong> as validators are
 * typically shared across repository instances.
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>Implementations must be safe for concurrent validation by multiple threads.</li>
 *   <li>Avoid storing per-query state in instance fields.</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <ul>
 *   <li>Validation should be fast compared to query execution.</li>
 *   <li>Consider caching validation results for frequently used queries.</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Use validation to prevent injection attacks.</li>
 *   <li>Validate that queries only access allowed fields/tables.</li>
 * </ul>
 */
public interface QueryValidator {
    /**
     * Validates a select query before execution.
     *
     * <p>Checks for common issues like invalid field names, type mismatches,
     * and potential security vulnerabilities.
     *
     * <p><strong>Performance:</strong> Should be much faster than executing the query.</p>
     * <p><strong>Thread Safety:</strong> Implementations must be thread-safe.</p>
     *
     * @param query the query to validate; must not be null
     * @return estimation of validation result with details about any issues
     */
    ValidationEstimation validateSelectQuery(SelectQuery query);

    /**
     * Validates a delete query before execution.
     *
     * <p>Checks for dangerous operations like missing WHERE clauses,
     * invalid filters, and unauthorized table access.
     *
     * <p><strong>Security:</strong> Pay special attention to queries that might
     * affect large numbers of records unintentionally.</p>
     * <p><strong>Thread Safety:</strong> Implementations must be thread-safe.</p>
     *
     * @param query the query to validate; must not be null
     * @return estimation of validation result with details about any issues
     */
    ValidationEstimation validateDeleteQuery(DeleteQuery query);

    /**
     * Validates an update query before execution.
     *
     * <p>Checks for invalid field assignments, type mismatches,
     * and potentially dangerous bulk updates.
     *
     * <p><strong>Security:</strong> Validate that updates don't expose sensitive data
     * or modify protected fields.</p>
     * <p><strong>Thread Safety:</strong> Implementations must be thread-safe.</p>
     *
     * @param query the query to validate; must not be null
     * @return estimation of validation result with details about any issues
     */
    ValidationEstimation validateUpdateQuery(UpdateQuery query);
}
