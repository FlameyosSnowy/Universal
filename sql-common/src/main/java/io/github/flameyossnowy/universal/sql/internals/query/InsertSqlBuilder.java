package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static io.github.flameyossnowy.universal.sql.internals.query.RepositoryDdlBuilder.hasPhysicalColumn;

public final class InsertSqlBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;

    public InsertSqlBuilder(QueryParseEngine.SQLType sqlType, RepositoryModel<T, ID> repositoryInformation) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
    }

    public ParameterizedSql parseInsert() {
        StringJoiner columnJoiner = new StringJoiner(", ");
        StringJoiner placeholderJoiner = new StringJoiner(", ");
        List<String> paramNames = new ArrayList<>(8);

        for (FieldModel<T> data : repositoryInformation.fields()) {
            if ((Collection.class.isAssignableFrom(data.type()) || Map.class.isAssignableFrom(data.type())) && !data.isJson()) continue;

            // Add companion version column for @JsonVersioned fields BEFORE the main column.
            if (data.isJson() && data.jsonVersioned()) {
                String versionColumn = data.columnName() + "_version";
                if (!hasPhysicalColumn(versionColumn, repositoryInformation)) {
                    columnJoiner.add(versionColumn);
                    placeholderJoiner.add("?");
                    paramNames.add(versionColumn);
                }
            }

            columnJoiner.add(data.columnName());

            if (data.autoIncrement()) {
                placeholderJoiner.add("default");
            } else {
                if (data.isJson()) {
                    if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) placeholderJoiner.add("?::jsonb");
                    else placeholderJoiner.add("?");
                } else {
                    placeholderJoiner.add("?");
                }
                paramNames.add(data.columnName());
            }
        }

        char q = sqlType.quoteChar();
        String sql = "INSERT INTO " + q + repositoryInformation.tableName() + q
            + " (" + columnJoiner + ") VALUES (" + placeholderJoiner + ");";

        return ParameterizedSql.of(sql, paramNames);
    }
}