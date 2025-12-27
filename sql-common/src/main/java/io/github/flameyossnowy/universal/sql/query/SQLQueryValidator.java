package io.github.flameyossnowy.universal.sql.query;

import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Query validator for SQL databases (MySQL, PostgreSQL, SQLite, etc.).
 * <p>
 * Validates queries against SQL-specific constraints:
 * - SQL injection prevention
 * - Valid SQL operators
 * - Index usage recommendations
 * - JOIN optimization hints
 */
public record SQLQueryValidator(RepositoryInformation repositoryInformation, SQLDialect dialect) implements QueryValidator {

    /**
     * SQL dialect for dialect-specific validation.
     */
    public enum SQLDialect {
        MYSQL,
        POSTGRESQL,
        SQLITE,
        MSSQL,
        ORACLE,
        GENERIC
    }

    // Pattern to detect potential SQL injection attempts
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        ".*('|(--)|(/\\*)|(;)|(\\bOR\\b)|(\\bAND\\b)|(\\bUNION\\b)|(\\bDROP\\b)|(\\bDELETE\\b)|(\\bINSERT\\b)|(\\bUPDATE\\b)).*",
        Pattern.CASE_INSENSITIVE
    );

    public SQLQueryValidator(RepositoryInformation repositoryInformation) {
        this(repositoryInformation, SQLDialect.GENERIC);
    }

    @Override
    public ValidationEstimation validateSelectQuery(SelectQuery query) {
        // Validate limit
        if (query.limit() < 1) {
            return ValidationEstimation.fail("Limit must be greater than 0");
        }

        // Warn about very large limits
        if (query.limit() > 50000) {
            Logging.warn(
                "Limit of " + query.limit() + " is very large and may cause performance issues. " +
                "Consider using pagination with smaller page sizes."
            );
        }

        // Validate filters
        List<SelectOption> filters = query.filters();
        for (SelectOption filter : filters) {
            // Validate field exists
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field == null) {
                return ValidationEstimation.fail("Filter field '" + filter.option() + "' does not exist in schema");
            }

            // Validate operator
            String operator = filter.operator();
            if (isInvalidSQLOperator(operator)) {
                return ValidationEstimation.fail(
                    "Operator '" + operator + "' is not a valid SQL operator. " +
                    "Valid operators: =, !=, <>, <, <=, >, >=, IN, NOT IN, LIKE, NOT LIKE, IS NULL, IS NOT NULL, BETWEEN"
                );
            }

            // Check for potential SQL injection in string values
            if (filter.value() instanceof String value) {
                if (containsSQLInjectionRisk(value)) {
                    return ValidationEstimation.fail(
                        "Filter value for field '" + filter.option() + "' contains potentially dangerous characters. " +
                        "Use parameterized queries to prevent SQL injection."
                    );
                }
            }

            // Warn about LIKE queries without indexes
            if (operator.equalsIgnoreCase("LIKE") && field.notIndexed() && !field.primary()) {
                Logging.warn(
                    "LIKE query on unindexed field '" + filter.option() + "' can be very slow. " +
                    "Consider adding an index or using full-text search for text queries."
                );
            }

            // Warn about leading wildcard in LIKE
            if (operator.equalsIgnoreCase("LIKE") && filter.value() instanceof String value) {
                if (!value.isEmpty() && value.charAt(0) == '%') {
                    Logging.warn(
                        "LIKE query with leading wildcard on field '" + filter.option() + "' cannot use indexes. " +
                        "This will result in a full table scan."
                    );
                }
            }
        }

        // Validate sort options
        if (!query.sortOptions().isEmpty()) {
            for (var sortOption : query.sortOptions()) {
                FieldData<?> field = repositoryInformation.getField(sortOption.field());
                if (field == null) {
                    return ValidationEstimation.fail("Sort field '" + sortOption.field() + "' does not exist in schema");
                }

                // Warn about sorting on unindexed fields
                if (!field.primary() && field.notIndexed() && filters.isEmpty()) {
                    Logging.warn(
                        "ORDER BY on unindexed field '" + sortOption.field() + "' without WHERE clause " +
                        "will cause a full table scan and filesort. Consider adding an index."
                    );
                }
            }
        }

        // Validate column selection
        if (!query.columns().isEmpty()) {
            for (String column : query.columns()) {
                FieldData<?> field = repositoryInformation.getField(column);
                if (field == null) {
                    return ValidationEstimation.fail("Selected column '" + column + "' does not exist in schema");
                }
            }
        }

        // Dialect-specific validations
        ValidationEstimation dialectValidation = validateSelectDialectSpecific(query);
        if (dialectValidation.isFail()) {
            return dialectValidation;
        }

        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateDeleteQuery(DeleteQuery query) {
        List<SelectOption> filters = query.filters();

        if (filters.isEmpty()) {
            return ValidationEstimation.fail(
                "DELETE without WHERE clause will delete all rows in the table. " +
                "If this is intentional, use TRUNCATE TABLE for better performance."
            );
        }

        // Validate all filter fields
        for (SelectOption filter : filters) {
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field == null) {
                return ValidationEstimation.fail("Filter field '" + filter.option() + "' does not exist in schema");
            }

            // Validate operator
            if (isInvalidSQLOperator(filter.operator())) {
                return ValidationEstimation.fail(
                    "Operator '" + filter.operator() + "' is not a valid SQL operator"
                );
            }

            // Check for SQL injection
            if (filter.value() instanceof String value) {
                if (containsSQLInjectionRisk(value)) {
                    return ValidationEstimation.fail(
                        "Filter value contains potentially dangerous characters. " +
                        "Use parameterized queries to prevent SQL injection."
                    );
                }
            }
        }

        // Warn about unindexed deletes
        if (filters.size() == 1) {
            SelectOption filter = filters.getFirst();
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field != null && !field.primary() && field.notIndexed()) {
                Logging.warn(
                    "DELETE with WHERE clause on unindexed field '" + filter.option() + "' may be slow. " +
                    "Consider adding an index or using primary key for deletion."
                );
            }
        }

        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateUpdateQuery(UpdateQuery query) {
        Map<String, Object> updates = query.updates();
        List<SelectOption> conditions = query.filters();

        // Validate update fields
        if (updates.isEmpty()) {
            return ValidationEstimation.fail("UPDATE must specify at least one field to update");
        }

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            // Validate field exists
            FieldData<?> field = repositoryInformation.getField(fieldName);
            if (field == null) {
                return ValidationEstimation.fail("Update field '" + fieldName + "' does not exist in schema");
            }

            // Warn about updating primary keys (usually not recommended)
            if (field.primary()) {
                return ValidationEstimation.fail(
                    "Updating primary key field '" + fieldName + "' is not recommended. " +
                    "Primary keys should be immutable. Consider using a different approach."
                );
            }

            // Check for SQL injection in string values
            if (value instanceof String strValue) {
                if (containsSQLInjectionRisk(strValue)) {
                    return ValidationEstimation.fail(
                        "Update value for field '" + fieldName + "' contains potentially dangerous characters. " +
                        "Use parameterized queries to prevent SQL injection."
                    );
                }
            }
        }

        // Validate WHERE filters
        if (conditions.isEmpty()) {
            return ValidationEstimation.fail(
                "UPDATE without WHERE clause will update all rows in the table. " +
                "This is rarely intentional and can cause data corruption."
            );
        }

        // Validate condition fields
        for (SelectOption condition : conditions) {
            FieldData<?> field = repositoryInformation.getField(condition.option());
            if (field == null) {
                return ValidationEstimation.fail("Condition field '" + condition.option() + "' does not exist in schema");
            }

            // Validate operator
            if (isInvalidSQLOperator(condition.operator())) {
                return ValidationEstimation.fail(
                    "Operator '" + condition.operator() + "' is not a valid SQL operator"
                );
            }

            // Check for SQL injection
            if (condition.value() instanceof String value) {
                if (containsSQLInjectionRisk(value)) {
                    return ValidationEstimation.fail(
                        "Condition value contains potentially dangerous characters. " +
                        "Use parameterized queries to prevent SQL injection."
                    );
                }
            }
        }

        // Warn about unindexed updates
        if (conditions.size() == 1) {
            SelectOption condition = conditions.getFirst();
            FieldData<?> field = repositoryInformation.getField(condition.option());
            if (field != null && !field.primary() && field.notIndexed()) {
                Logging.warn(
                    "UPDATE with WHERE clause on unindexed field '" + condition.option() + "' may be slow. " +
                    "Consider adding an index or using primary key for updates."
                );
            }
        }

        return ValidationEstimation.PASS;
    }

    /**
     * Validates if the operator is supported in SQL.
     */
    private static boolean isInvalidSQLOperator(String operator) {
        return switch (operator.toUpperCase()) {
            // Comparison operators
            case "=", "!=", "<>", "<", "<=", ">", ">=" -> false;
            // Pattern matching
            case "LIKE", "NOT LIKE", "ILIKE", "SIMILAR TO", "~", "~*" -> false;
            // Range operators
            case "BETWEEN", "NOT BETWEEN" -> false;
            // Set operators
            case "IN", "NOT IN" -> false;
            // NULL checks
            case "IS NULL", "IS NOT NULL" -> false;
            // Boolean operators (handled in WHERE clause)
            case "AND", "OR", "NOT" -> false;
            default -> true;
        };
    }

    /**
     * Checks if a string value contains potential SQL injection patterns.
     * Note: This is a basic check. Always use parameterized queries!
     */
    private static boolean containsSQLInjectionRisk(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // Check for common SQL injection patterns
        return SQL_INJECTION_PATTERN.matcher(value).matches();
    }

    /**
     * Performs dialect-specific validation for SELECT queries.
     */
    private ValidationEstimation validateSelectDialectSpecific(SelectQuery query) {
        return switch (dialect) {
            case MYSQL -> validateMySQLSelect(query);
            case POSTGRESQL -> validatePostgreSQLSelect(query);
            case SQLITE -> validateSQLiteSelect(query);
            default -> ValidationEstimation.PASS;
        };
    }

    private static ValidationEstimation validateMySQLSelect(SelectQuery query) {
        // MySQL-specific validations
        if (query.limit() > 0 && query.sortOptions().isEmpty()) {
            // LIMIT without ORDER BY can return unpredictable results
            Logging.warn(
                "MySQL: Using LIMIT without ORDER BY can return unpredictable results. " +
                "Add ORDER BY clause for consistent pagination."
            );
        }
        return ValidationEstimation.PASS;
    }

    private static ValidationEstimation validatePostgreSQLSelect(SelectQuery ignoredQuery) {
        // PostgreSQL-specific validations
        // PostgreSQL has excellent query planner, fewer warnings needed
        return ValidationEstimation.PASS;
    }

    private static ValidationEstimation validateSQLiteSelect(SelectQuery query) {
        // SQLite-specific validations
        if (query.limit() > 10000) {
            Logging.warn(
                "SQLite: Large LIMIT values can cause memory issues in SQLite. " +
                "Consider using smaller page sizes for pagination."
            );
        }
        return ValidationEstimation.PASS;
    }
}
