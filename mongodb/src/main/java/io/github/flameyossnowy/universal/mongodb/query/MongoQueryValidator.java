package io.github.flameyossnowy.universal.mongodb.query;

import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Query validator for MongoDB queries.
 * <p>
 * Validates queries against MongoDB-specific constraints:
 * - Field names cannot contain dots or start with $
 * - Operators must be valid MongoDB operators
 * - Collection scan warnings for unindexed queries
 * - Document size limits
 */
public record MongoQueryValidator(RepositoryInformation repositoryInformation) implements QueryValidator {

    private static final int MAX_DOCUMENT_SIZE = 16 * 1024 * 1024; // 16MB
    private static final Pattern INVALID_FIELD_PATTERN = Pattern.compile(".*\\..*|^\\$.*");

    private static final Set<String> VALID_REGEXES = new HashSet<>(2);
    private static final Set<String> INVALID_REGEXES = new HashSet<>(2);

    @Override
    public ValidationEstimation validateSelectQuery(SelectQuery query) {
        // Validate limit
        if (query.limit() > 0 && query.limit() < 1) {
            return ValidationEstimation.fail("Limit must be greater than 0");
        }

        // MongoDB supports very large limits, but warn for extremely large values
        if (query.limit() > 100000) {
            Logging.warn(
                "Limit of " + query.limit() + " is very large and may cause memory issues. " +
                "Consider using pagination or reducing the limit."
            );
        }

        // Validate filters
        List<SelectOption> filters = query.filters();
        for (SelectOption filter : filters) {
            // Validate field exists
            FieldData<?> field = repositoryInformation.getField(filter.option());
            ValidationEstimation validationEstimation = getValidationEstimation(filter, field);
            if (validationEstimation != null) return validationEstimation;

            // Validate operator
            String operator = filter.operator();
            if (isInvalidMongoOperator(operator)) {
                return ValidationEstimation.fail(
                    "Operator '" + operator + "' is not a valid MongoDB operator. " +
                    "Valid operators: =, !=, <, <=, >, >=, IN, NOT IN, REGEX, EXISTS, TYPE, SIZE, ALL, ELEM_MATCH"
                );
            }

            // Warn about unindexed fields (performance)
            if (!field.primary() && field.notIndexed() && filters.size() == 1) {
                // Single unindexed filter may cause collection scan
                Logging.warn(
                    "Filter field '" + filter.option() + "' is not indexed. " +
                    "This query will perform a collection scan which can be slow on large collections. " +
                    "Consider adding an index on this field."
                );
            }

            String value = filter.value().toString();
            ValidationEstimation regexValidation = validateRegex(filter, operator, value);
            if (regexValidation != null) return regexValidation;
        }

        // Validate sort options
        ValidationEstimation sortOption = validateSortOptions(query);
        if (sortOption != null) return sortOption;

        if (query.columns().isEmpty()) {
            return ValidationEstimation.PASS;
        }

        for (String column : query.columns()) {
            FieldData<?> field = repositoryInformation.getField(column);
            if (field == null) return ValidationEstimation.fail("Selected column '" + column + "' does not exist in schema");
        }

        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateDeleteQuery(DeleteQuery query) {
        List<SelectOption> filters = query.filters();

        if (filters.isEmpty()) {
            return ValidationEstimation.fail(
                "DELETE without WHERE clause will delete all documents in the collection. " +
                "If this is intentional, use a specific method for clearing the collection."
            );
        }

        // Validate all filter fields
        for (SelectOption filter : filters) {
            FieldData<?> field = repositoryInformation.getField(filter.option());
            ValidationEstimation validationEstimation = getValidationEstimation(filter, field);
            if (validationEstimation != null) return validationEstimation;

            // Validate operator
            if (isInvalidMongoOperator(filter.operator())) {
                return ValidationEstimation.fail(
                    "Operator '" + filter.operator() + "' is not a valid MongoDB operator"
                );
            }
        }

        // Warn about unindexed deletes (performance)
        if (filters.size() == 1) {
            SelectOption filter = filters.getFirst();
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field != null && !field.primary() && field.notIndexed()) {
                Logging.warn(
                    "DELETE filter on unindexed field '" + filter.option() + "' may be slow. " +
                    "Consider adding an index or using _id for deletion."
                );
            }
        }

        return ValidationEstimation.PASS;
    }

    private @Nullable ValidationEstimation getValidationEstimation(SelectOption filter, FieldData<?> field) {
        if (field == null) {
            return ValidationEstimation.fail("Filter field '" + filter.option() + "' does not exist in schema");
        }

        // Validate field name
        if (INVALID_FIELD_PATTERN.matcher(filter.option()).matches()) {
            return ValidationEstimation.fail(
                    "Field name '" + filter.option() + "' is invalid. " +
                            "MongoDB field names cannot contain dots or start with $"
            );
        }
        return null;
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

            // Validate field name
            if (INVALID_FIELD_PATTERN.matcher(fieldName).matches()) {
                return ValidationEstimation.fail(
                    "Field name '" + fieldName + "' is invalid. " +
                    "MongoDB field names cannot contain dots or start with $"
                );
            }

            // Cannot update _id field
            if (fieldName.equals("_id") || field.primary()) {
                return ValidationEstimation.fail(
                    "Cannot update primary key field '" + fieldName + "'. " +
                    "The _id field is immutable in MongoDB."
                );
            }

            // Validate value size (rough estimate)
            if (value != null) {
                String valueStr = value.toString();
                if (valueStr.length() > MAX_DOCUMENT_SIZE / 2) {
                    return ValidationEstimation.fail(
                        "Update value for field '" + fieldName + "' is very large. " +
                        "MongoDB documents have a 16MB size limit."
                    );
                }
            }
        }

        // Validate WHERE filters
        if (conditions.isEmpty()) {
            Logging.warn("UPDATE without WHERE clause will update all documents.");
        }

        // Validate condition fields
        for (SelectOption condition : conditions) {
            FieldData<?> field = repositoryInformation.getField(condition.option());
            if (field == null) {
                return ValidationEstimation.fail("Condition field '" + condition.option() + "' does not exist in schema");
            }

            // Validate field name
            if (INVALID_FIELD_PATTERN.matcher(condition.option()).matches()) {
                return ValidationEstimation.fail(
                    "Field name '" + condition.option() + "' is invalid. " +
                    "MongoDB field names cannot contain dots or start with $"
                );
            }

            // Validate operator
            if (isInvalidMongoOperator(condition.operator())) {
                return ValidationEstimation.fail(
                    "Operator '" + condition.operator() + "' is not a valid MongoDB operator"
                );
            }
        }

        // Warn about unindexed updates
        if (conditions.size() == 1) {
            SelectOption condition = conditions.getFirst();
            FieldData<?> field = repositoryInformation.getField(condition.option());
            if (field != null && !field.primary() && field.notIndexed()) {
                Logging.warn(
                    "UPDATE condition on unindexed field '" + condition.option() + "' may be slow. " +
                    "Consider adding an index or using _id for updates."
                );
            }
        }

        return ValidationEstimation.PASS;
    }

    private @Nullable ValidationEstimation validateSortOptions(SelectQuery query) {
        if (query.sortOptions().isEmpty()) {
            return null;
        }
        for (var sortOption : query.sortOptions()) {
            FieldData<?> field = repositoryInformation.getField(sortOption.field());
            if (field == null) {
                return ValidationEstimation.fail("Sort field '" + sortOption.field() + "' does not exist in schema");
            }

            if (!field.primary() && field.notIndexed()) {
                Logging.warn(
                        "Sort field '" + sortOption.field() + "' is not indexed. " +
                                "Sorting on unindexed fields can be slow and may fail if result set exceeds 32MB. " +
                                "Consider adding an index on this field."
                );
            }
        }
        return null;
    }

    // Optimization: Must cache regex compilation and analyzation because they are expensive
    // Regex is like a mini-interpreter, lol
    private static @Nullable ValidationEstimation validateRegex(SelectOption filter, @NotNull String operator, String value) {
        boolean regex = operator.equalsIgnoreCase("REGEX");

        if (!regex) return null;

        if (VALID_REGEXES.contains(value)) {
            return null;
        }

        if (INVALID_REGEXES.contains(value)) {
            return ValidationEstimation.fail("Invalid regex pattern for field '" + filter.option() + "'");
        }

        try {
            Pattern.compile(value);
            VALID_REGEXES.add(value);
        } catch (Exception e) {
            INVALID_REGEXES.add(value);
            return ValidationEstimation.fail("Invalid regex pattern for field '" + filter.option() + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Validates if the operator is supported in MongoDB.
     */
    private boolean isInvalidMongoOperator(String operator) {
        return switch (operator.toUpperCase()) {
            // Comparison operators
            case "=", "EQ", "!=", "NE", "<", "LT", "<=", "LTE", ">", "GT", ">=", "GTE" -> false;
            // Array operators
            case "IN", "NOT IN", "NIN", "ALL", "SIZE", "ELEM_MATCH" -> false;
            // Element operators
            case "EXISTS", "TYPE" -> false;
            // Evaluation operators
            case "REGEX", "MOD", "TEXT", "WHERE" -> false;
            // Logical operators (handled separately)
            case "AND", "OR", "NOT", "NOR" -> false;
            default -> true;
        };
    }
}
