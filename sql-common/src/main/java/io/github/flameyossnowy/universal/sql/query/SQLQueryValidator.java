package io.github.flameyossnowy.universal.sql.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record SQLQueryValidator<T, ID>(
    RepositoryModel<T, ID> repositoryInformation,
    SQLDialect dialect
) implements QueryValidator {

    public enum SQLDialect {
        MYSQL,
        POSTGRESQL,
        SQLITE,
        MSSQL,
        ORACLE,
        GENERIC
    }

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        ".*('|(-- )|(/\\*)|(;)|(\\bOR\\b)|(\\bAND\\b)|(\\bUNION\\b)|(\\bDROP\\b)).*",
        Pattern.CASE_INSENSITIVE
    );

    public SQLQueryValidator(RepositoryModel<T, ID> repositoryInformation) {
        this(repositoryInformation, SQLDialect.GENERIC);
    }

    // ------------------------------------------------------------------------
    // SELECT
    // ------------------------------------------------------------------------

    @Override
    public ValidationEstimation validateSelectQuery(SelectQuery query) {

        if (query.limit() < 1) {
            return ValidationEstimation.fail("Limit must be greater than 0");
        }

        if (query.limit() > 50_000) {
            Logging.warn("Very large LIMIT detected; consider pagination");
        }

        for (FilterOption filter : query.filters()) {

            if (filter instanceof SelectOption so) {
                ValidationEstimation v = validateSelectOption(so);
                if (v.isFail()) return v;
            }

            else if (filter instanceof JsonSelectOption jso) {
                ValidationEstimation v = validateJsonSelectOption(jso);
                if (v.isFail()) return v;
            }

            else {
                return ValidationEstimation.fail(
                    "Unknown FilterOption type: " + filter.getClass().getName()
                );
            }
        }

        // ORDER BY
        for (var sort : query.sortOptions()) {
            FieldModel<T> field = repositoryInformation.fieldByName(sort.field());
            if (field == null) {
                return ValidationEstimation.fail(
                    "Sort field '" + sort.field() + "' does not exist"
                );
            }

            if (!field.id() && field.notIndexed() && query.filters().isEmpty()) {
                Logging.warn(
                    "ORDER BY on unindexed field '" + sort.field() +
                        "' without WHERE may be slow"
                );
            }
        }

        // Column projection
        for (String column : query.columns()) {
            if (repositoryInformation.fieldByName(column) == null) {
                return ValidationEstimation.fail(
                    "Selected column '" + column + "' does not exist"
                );
            }
        }

        return validateSelectDialectSpecific(query);
    }

    private ValidationEstimation validateSelectOption(SelectOption option) {

        FieldModel<T> field = repositoryInformation.fieldByName(option.option());
        if (field == null) {
            return ValidationEstimation.fail(
                "Field '" + option.option() + "' does not exist"
            );
        }

        if (isInvalidSQLOperator(option.operator())) {
            return ValidationEstimation.fail(
                "Invalid SQL operator '" + option.operator() + "'"
            );
        }

        if (option.value() instanceof String s && containsSQLInjectionRisk(s)) {
            return ValidationEstimation.fail(
                "Potentially unsafe value for field '" + option.option() + "'"
            );
        }

        return ValidationEstimation.PASS;
    }

    private ValidationEstimation validateJsonSelectOption(JsonSelectOption option) {

        FieldModel<T> field = repositoryInformation.fieldByName(option.field());
        if (field == null) {
            return ValidationEstimation.fail(
                "JSON field '" + option.field() + "' does not exist"
            );
        }

        if (!field.isJson()) {
            return ValidationEstimation.fail(
                "Field '" + option.field() + "' is not a JSON field"
            );
        }

        if (!field.jsonQueryable()) {
            return ValidationEstimation.fail(
                "JSON querying is disabled for field '" + option.field() + "'"
            );
        }

        if (!isValidJsonPath(option.jsonPath())) {
            return ValidationEstimation.fail(
                "Invalid JSON path '" + option.jsonPath() + "'"
            );
        }

        if (isInvalidJsonOperator(option.operator())) {
            return ValidationEstimation.fail(
                "Invalid JSON operator '" + option.operator() + "'"
            );
        }

        if (option.value() instanceof String s && containsSQLInjectionRisk(s)) {
            return ValidationEstimation.fail(
                "Potentially unsafe JSON value for field '" + option.field() + "'"
            );
        }

        return ValidationEstimation.PASS;
    }

    // ------------------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------------------

    @Override
    public ValidationEstimation validateDeleteQuery(DeleteQuery query) {

        if (query.filters().isEmpty()) {
            return ValidationEstimation.fail(
                "DELETE without WHERE is forbidden"
            );
        }

        for (FilterOption filter : query.filters()) {
            if (filter instanceof SelectOption so) {
                ValidationEstimation v = validateSelectOption(so);
                if (v.isFail()) return v;
            } else if (filter instanceof JsonSelectOption jso) {
                ValidationEstimation v = validateJsonSelectOption(jso);
                if (v.isFail()) return v;
            }
        }

        return ValidationEstimation.PASS;
    }

    // ------------------------------------------------------------------------
    // UPDATE
    // ------------------------------------------------------------------------

    @Override
    public ValidationEstimation validateUpdateQuery(UpdateQuery query) {
        Map<String, Object> updates = query.updates();

        if (updates.isEmpty()) {
            return ValidationEstimation.fail("UPDATE must set at least one field");
        }

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            FieldModel<T> field = repositoryInformation.fieldByName(entry.getKey());

            if (field == null) {
                return ValidationEstimation.fail(
                    "Update field '" + entry.getKey() + "' does not exist"
                );
            }

            if (field.id()) {
                return ValidationEstimation.fail(
                    "Updating primary key '" + entry.getKey() + "' is not allowed"
                );
            }
        }

        if (query.filters().isEmpty()) {
            return ValidationEstimation.fail(
                "UPDATE without WHERE is forbidden"
            );
        }

        for (FilterOption filter : query.filters()) {
            if (filter instanceof SelectOption so) {
                ValidationEstimation v = validateSelectOption(so);
                if (v.isFail()) return v;
            } else if (filter instanceof JsonSelectOption jso) {
                ValidationEstimation v = validateJsonSelectOption(jso);
                if (v.isFail()) return v;
            }
        }

        return ValidationEstimation.PASS;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static boolean isInvalidSQLOperator(String op) {
        return switch (op.toUpperCase()) {
            case "=", "!=", "<>", "<", "<=", ">", ">=",
                 "IN", "NOT IN",
                 "LIKE", "NOT LIKE",
                 "BETWEEN",
                 "IS NULL", "IS NOT NULL" -> false;
            default -> true;
        };
    }

    private static boolean isInvalidJsonOperator(String op) {
        return switch (op.toUpperCase()) {
            case "=", "!=", "@>", "<@", "?", "?|", "?&",
                 "LIKE", "ILIKE" -> false;
            default -> true;
        };
    }

    private static boolean isValidJsonPath(String path) {
        return path != null && path.startsWith("$.") && path.length() > 2;
    }

    private static boolean containsSQLInjectionRisk(String value) {
        return SQL_INJECTION_PATTERN.matcher(value).matches();
    }

    private ValidationEstimation validateSelectDialectSpecific(SelectQuery query) {
        return switch (dialect) {
            case MYSQL -> {
                if (query.limit() > 0 && query.sortOptions().isEmpty()) {
                    Logging.warn("MySQL: LIMIT without ORDER BY is nondeterministic");
                }
                yield ValidationEstimation.PASS;
            }
            case SQLITE -> {
                if (query.limit() > 10_000) {
                    Logging.warn("SQLite: very large LIMIT may be slow");
                }
                yield ValidationEstimation.PASS;
            }
            default -> ValidationEstimation.PASS;
        };
    }
}