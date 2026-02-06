package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

public final class SelectSqlBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;
    private final SqlConditionBuilder<T, ID> conditionBuilder;
    private final SqlSortBuilder sortBuilder;

    public SelectSqlBuilder(
        QueryParseEngine.SQLType sqlType,
        RepositoryModel<T, ID> repositoryInformation,
        SqlConditionBuilder<T, ID> conditionBuilder,
        SqlSortBuilder sortBuilder
    ) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.conditionBuilder = conditionBuilder;
        this.sortBuilder = sortBuilder;
    }

    public String parseSelect(SelectQuery query, boolean first) {
        String tableName = repositoryInformation.tableName();

        if (query == null) {
            return "SELECT * FROM " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + (first ? " LIMIT 1" : "");
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM " + sqlType.quoteChar())
            .append(tableName)
            .append(sqlType.quoteChar());

        appendConditions(query, sql);
        appendSortingAndLimit(query, sql, first);

        return sql.toString();
    }

    public String parseQueryIds(SelectQuery query, boolean first) {
        String tableName = repositoryInformation.tableName();

        FieldModel<T> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Cannot find Id because it doesn't exist.");
        }

        String idName = primaryKey.name();

        if (query == null) {
            return "SELECT " + idName + " FROM " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + (first ? " LIMIT 1" : "");
        }

        StringBuilder sql = new StringBuilder("SELECT " + idName + " FROM " + sqlType.quoteChar())
            .append(tableName)
            .append(sqlType.quoteChar());

        appendConditions(query, sql);
        appendSortingAndLimit(query, sql, first);

        return sql.toString();
    }

    private void appendConditions(SelectQuery query, StringBuilder sql) {
        if (!query.filters().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(conditionBuilder.buildConditions(query.filters()));
        }
    }

    private void appendSortingAndLimit(SelectQuery query, StringBuilder sql, boolean first) {
        if (!query.sortOptions().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(sortBuilder.buildSortOptions(query.sortOptions()));
        }

        if (query.limit() != -1) {
            sql.append(" LIMIT ").append(query.limit());
        } else if (first) {
            sql.append(" LIMIT 1");
        }
    }
}
