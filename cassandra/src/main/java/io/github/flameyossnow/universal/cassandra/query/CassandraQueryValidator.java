package io.github.flameyossnow.universal.cassandra.query;

import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query validator for Cassandra CQL queries.
 * <p>
 * Validates queries against Cassandra-specific constraints:
 * - Partition key must be specified in WHERE clauses
 * - Clustering columns must be used in order
 * - Secondary indexes required for non-key columns
 * - ALLOW FILTERING implications
 */
public record CassandraQueryValidator(RepositoryInformation repositoryInformation) implements QueryValidator {

    @Override
    public ValidationEstimation validateSelectQuery(SelectQuery query) {
        // Validate limit
        if (query.limit() < 1) {
            return ValidationEstimation.fail("Limit must be greater than 0");
        }

        // Validate filters exist and are valid
        List<SelectOption> filters = query.filters();
        if (filters.isEmpty()) {
            return ValidationEstimation.PASS;
        }

        for (SelectOption filter : filters) {
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field == null) {
                return ValidationEstimation.fail("Filter field '" + filter.option() + "' does not exist in schema");
            }

            String operator = filter.operator();
            if (isInvalidCassandraOperator(operator)) {
                return ValidationEstimation.fail("Operator '" + operator + "' is not supported in Cassandra");
            }

            if (!field.primary() && field.notIndexed()) {
                Logging.warn(
                    "Filter field '" + filter.option() + "' is not a primary key or indexed. " +
                    "This requires ALLOW FILTERING which can cause performance issues. " +
                    "Consider adding a secondary index or including partition key in query."
                );
            }
        }

        // Validate partition key is included for efficient queries
        Set<String> filterFields = new HashSet<>(filters.size());
        for (SelectOption filter : filters) {
            filterFields.add(filter.option());
        }

        List<FieldData<?>> primaryKeys = repositoryInformation.getPrimaryKeys();
        if (!primaryKeys.isEmpty()) {
            FieldData<?> partitionKey = primaryKeys.getFirst(); // First primary key is partition key
            if (!filterFields.contains(partitionKey.name())) {
                Logging.warn(
                    "Partition key '" + partitionKey.name() + "' should be included in WHERE clause " +
                    "for efficient queries. Queries without partition key require full table scan."
                );
            }
        }

        // Validate sort options
        if (!query.sortOptions().isEmpty()) {
            // Cassandra only allows ORDER BY on clustering columns
            for (var sortOption : query.sortOptions()) {
                FieldData<?> field = repositoryInformation.getField(sortOption.field());
                if (field == null) {
                    return ValidationEstimation.fail("Sort field '" + sortOption.field() + "' does not exist");
                }
                // In Cassandra, only clustering columns (secondary primary keys) can be used for ORDER BY
                if (!field.primary() || primaryKeys.indexOf(field) == 0) {
                    return ValidationEstimation.fail(
                        "ORDER BY is only allowed on clustering columns in Cassandra. " +
                        "Field '" + sortOption.field() + "' is not a clustering column."
                    );
                }
            }
        }

        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateDeleteQuery(DeleteQuery query) {
        List<SelectOption> filters = query.filters();
        
        if (filters.isEmpty()) {
            return ValidationEstimation.fail(
                "DELETE without WHERE clause is not allowed in Cassandra. " +
                "This would require TRUNCATE instead."
            );
        }

        // Validate all filter fields exist
        for (SelectOption filter : filters) {
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field == null) {
                return ValidationEstimation.fail("Filter field '" + filter.option() + "' does not exist in schema");
            }

            // Validate operator
            if (isInvalidCassandraOperator(filter.operator())) {
                return ValidationEstimation.fail("Operator '" + filter.operator() + "' is not supported in Cassandra");
            }
        }

        // Cassandra requires partition key in DELETE WHERE clause
        Set<String> filterFields = new HashSet<>(filters.size());
        for (SelectOption filter : filters) {
            filterFields.add(filter.option());
        }

        List<FieldData<?>> primaryKeys = repositoryInformation.getPrimaryKeys();
        if (!primaryKeys.isEmpty()) {
            FieldData<?> partitionKey = primaryKeys.getFirst();
            if (!filterFields.contains(partitionKey.name())) {
                return ValidationEstimation.fail(
                    "DELETE requires partition key '" + partitionKey.name() + "' in WHERE clause. " +
                    "Cassandra does not support DELETE without partition key."
                );
            }
        }

        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateUpdateQuery(UpdateQuery query) {
        Map<String, Object> updates = query.updates();
        List<SelectOption> conditions = query.filters();

        // Validate update fields exist and are not primary keys
        if (updates.isEmpty()) {
            return ValidationEstimation.fail("UPDATE must specify at least one field to update");
        }

        for (String updateField : updates.keySet()) {
            FieldData<?> field = repositoryInformation.getField(updateField);
            if (field == null) {
                return ValidationEstimation.fail("Update field '" + updateField + "' does not exist in schema");
            }

            // Cannot update primary key columns in Cassandra
            if (field.primary()) {
                return ValidationEstimation.fail(
                    "Cannot update primary key field '" + updateField + "' in Cassandra. " +
                    "Primary keys are immutable."
                );
            }
        }

        // Validate WHERE filters
        if (conditions.isEmpty()) {
            return ValidationEstimation.fail(
                "UPDATE without WHERE clause is not allowed in Cassandra. " +
                "Must specify partition key at minimum."
            );
        }

        // Validate condition fields exist
        for (SelectOption condition : conditions) {
            FieldData<?> field = repositoryInformation.getField(condition.option());
            if (field == null) {
                return ValidationEstimation.fail("Condition field '" + condition.option() + "' does not exist in schema");
            }

            // Validate operator
            if (isInvalidCassandraOperator(condition.operator())) {
                return ValidationEstimation.fail("Operator '" + condition.operator() + "' is not supported in Cassandra");
            }
        }

        // Cassandra requires partition key in UPDATE WHERE clause
        Set<String> conditionFields = new HashSet<>(conditions.size());
        for (SelectOption condition : conditions) {
            conditionFields.add(condition.option());
        }

        List<FieldData<?>> primaryKeys = repositoryInformation.getPrimaryKeys();
        if (!primaryKeys.isEmpty()) {
            FieldData<?> partitionKey = primaryKeys.getFirst();
            if (!conditionFields.contains(partitionKey.name())) {
                return ValidationEstimation.fail(
                    "UPDATE requires partition key '" + partitionKey.name() + "' in WHERE clause. " +
                    "Cassandra does not support UPDATE without partition key."
                );
            }
        }

        return ValidationEstimation.PASS;
    }

    /**
     * Validates if the operator is supported in Cassandra CQL.
     */
    private static boolean isInvalidCassandraOperator(String operator) {
        return !switch (operator.toUpperCase()) {
            case "=", "!=", "<", "<=", ">", ">=", "IN", "CONTAINS", "CONTAINS KEY" -> true;
            default -> false;
        };
    }
}
