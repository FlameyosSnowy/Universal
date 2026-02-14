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
            if (data.autoIncrement()) {
                joiner.add("default");
            } else {
                joiner.add("?");
            }
            columnJoiner.add(data.name());
        }

        queryBuilder.append(columnJoiner).append(") VALUES (");
        queryBuilder.append(joiner).append(')');
        return queryBuilder.toString();
    }
}
