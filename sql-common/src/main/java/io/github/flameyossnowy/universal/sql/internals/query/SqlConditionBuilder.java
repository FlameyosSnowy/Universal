package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.Collection;
import java.util.Collections;
import java.util.StringJoiner;

public final class SqlConditionBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;

    public SqlConditionBuilder(QueryParseEngine.SQLType sqlType, RepositoryModel<T, ID> repositoryInformation) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
    }

    public String buildConditions(Iterable<FilterOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");

        for (FilterOption filter : filters) {
            if (filter instanceof SelectOption s) {
                joiner.add(buildColumnCondition(s));
                continue;
            }

            if (filter instanceof JsonSelectOption j) {
                joiner.add(buildJsonCondition(j));
                continue;
            }

            throw new IllegalStateException("Unknown filter type: " + filter);
        }

        return joiner.toString();
    }

    private static String buildColumnCondition(SelectOption filter) {
        if ("IN".equalsIgnoreCase(filter.operator())) {
            Object value = filter.value();
            if (value instanceof Collection<?> list) {
                String placeholders = String.join(", ", Collections.nCopies(list.size(), "?"));
                return filter.option() + " IN (" + placeholders + ")";
            }
            return filter.option() + " IN (?)";
        }

        return filter.option() + " " + filter.operator() + " ?";
    }

    private String buildJsonCondition(JsonSelectOption filter) {
        FieldModel<T> field = repositoryInformation.fieldByName(filter.field());
        if (field == null) {
            throw new IllegalArgumentException(
                "Unknown field in JSON filter: " + filter.field()
            );
        }

        if (!field.isJson()) {
            throw new IllegalArgumentException(
                "Field '" + field.name() + "' is not a JSON field"
            );
        }

        return switch (sqlType) {
            case POSTGRESQL -> buildPostgresJsonCondition(field, filter);
            case MYSQL -> buildMySqlJsonCondition(field, filter);
            case SQLITE -> throw new UnsupportedOperationException(
                "SQLite does not support JSON filtering in Universal"
            );
        };
    }

    private String buildPostgresJsonCondition(
        FieldModel<T> field,
        JsonSelectOption filter
    ) {
        return field.name()
            + " #>> '{"
            + filter.jsonPath().replace("$.", "").replace(".", ",")
            + "}' "
            + filter.operator()
            + " ?";
    }

    private String buildMySqlJsonCondition(
        FieldModel<T> field,
        JsonSelectOption filter
    ) {
        return "JSON_UNQUOTE(JSON_EXTRACT("
            + field.name()
            + ", '"
            + filter.jsonPath()
            + "')) "
            + filter.operator()
            + " ?";
    }
}
