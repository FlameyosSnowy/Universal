package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public final class SqlConditionBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;

    public SqlConditionBuilder(QueryParseEngine.SQLType sqlType, RepositoryModel<T, ID> repositoryInformation) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
    }

    /**
     * Pairs a WHERE-clause SQL fragment with the ordered parameter names for
     * every {@code ?} placeholder it contains.
     */
    public record BuiltCondition(String sql, List<String> paramNames) {}

    public BuiltCondition buildConditionsFull(Iterable<FilterOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");
        List<String> paramNames = new ArrayList<>();

        for (FilterOption filter : filters) {
            if (filter instanceof SelectOption s) {
                BuiltCondition part = buildColumnConditionFull(s);
                joiner.add(part.sql());
                paramNames.addAll(part.paramNames());
                continue;
            }

            if (filter instanceof JsonSelectOption j) {
                BuiltCondition part = buildJsonConditionFull(j);
                joiner.add(part.sql());
                paramNames.addAll(part.paramNames());
                continue;
            }

            throw new IllegalStateException("Unknown filter type: " + filter);
        }

        return new BuiltCondition(joiner.toString(), paramNames);
    }

    public String buildConditions(Iterable<FilterOption> filters) {
        return buildConditionsFull(filters).sql();
    }

    private BuiltCondition buildColumnConditionFull(SelectOption filter) {
        FieldModel<T> field = repositoryInformation.fieldByName(filter.option());
        if (field == null) {
            throw new IllegalArgumentException("Unknown field in filter: " + filter.option());
        }

        String column = field.columnName();

        if ("IN".equalsIgnoreCase(filter.operator())) {
            Object value = filter.value();
            if (value instanceof Collection<?> list) {
                String placeholders = String.join(", ", Collections.nCopies(list.size(), "?"));
                // Each placeholder binds to the same logical column name.
                List<String> names = Collections.nCopies(list.size(), column);
                return new BuiltCondition(column + " IN (" + placeholders + ")", names);
            }
            return new BuiltCondition(column + " IN (?)", List.of(column));
        }

        if (filter.value() == null
            && ("IS NULL".equalsIgnoreCase(filter.operator())
            || "IS NOT NULL".equalsIgnoreCase(filter.operator()))) {
            return new BuiltCondition(column + " " + filter.operator(), List.of());
        }

        return new BuiltCondition(column + " " + filter.operator() + " ?", List.of(column));
    }

    private BuiltCondition buildJsonConditionFull(JsonSelectOption filter) {
        FieldModel<T> field = repositoryInformation.fieldByName(filter.field());
        if (field == null) {
            throw new IllegalArgumentException("Unknown field in JSON filter: " + filter.field());
        }

        if (!field.isJson()) {
            throw new IllegalArgumentException("Field '" + field.name() + "' is not a JSON field");
        }

        return switch (sqlType) {
            case POSTGRESQL -> buildPostgresJsonConditionFull(field, filter);
            case MYSQL      -> buildMySqlJsonConditionFull(field, filter);
            case SQLITE     -> throw new UnsupportedOperationException(
                "SQLite does not support JSON filtering in Universal");
        };
    }

    private BuiltCondition buildPostgresJsonConditionFull(FieldModel<T> field, JsonSelectOption filter) {
        String sql = field.columnName()
            + " #>> '{"
            + filter.jsonPath().replace("$.", "").replace(".", ",")
            + "}' "
            + filter.operator()
            + " ?";
        return new BuiltCondition(sql, List.of(field.columnName()));
    }

    private BuiltCondition buildMySqlJsonConditionFull(FieldModel<T> field, JsonSelectOption filter) {
        String sql = "JSON_UNQUOTE(JSON_EXTRACT("
            + field.columnName()
            + ", '"
            + filter.jsonPath()
            + "')) "
            + filter.operator()
            + " ?";
        return new BuiltCondition(sql, List.of(field.columnName()));
    }
}