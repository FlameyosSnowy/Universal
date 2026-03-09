package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

public final class InsertSqlBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;

    public InsertSqlBuilder(QueryParseEngine.SQLType sqlType, RepositoryModel<T, ID> repositoryInformation) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
    }

    public String parseInsert() {
        StringJoiner columnJoiner = new StringJoiner(", ");

        StringBuilder queryBuilder = new StringBuilder("INSERT INTO " + sqlType.quoteChar());
        queryBuilder.append(repositoryInformation.tableName()).append(sqlType.quoteChar()).append(" (");

        StringJoiner joiner = new StringJoiner(", ");
        for (FieldModel<T> data : repositoryInformation.fields()) {
            if (Collection.class.isAssignableFrom(data.type()) || Map.class.isAssignableFrom(data.type())) continue;

            // Add companion version column for @JsonVersioned fields.
            if (data.isJson() && data.jsonVersioned()) {
                String versionColumn = data.columnName() + "_version";
                if (!hasPhysicalColumn(versionColumn)) {
                    columnJoiner.add(versionColumn);
                    joiner.add("?");
                }
            }

            if (data.autoIncrement()) {
                joiner.add("default");
            } else {
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL && data.isJson()) {
                    joiner.add("?::jsonb");
                } else {
                    joiner.add("?");
                }
            }
            columnJoiner.add(data.columnName());
        }

        queryBuilder.append(columnJoiner).append(") VALUES (").append(joiner).append(");");
        return queryBuilder.toString();
    }

    private boolean hasPhysicalColumn(String columnName) {
        for (FieldModel<T> field : repositoryInformation.fields()) {
            if (field == null) continue;
            String col = field.columnName();
            if (col != null && col.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }
}
