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

    private final ParameterizedSql selectAll;
    private final ParameterizedSql selectFirst;
    private final ParameterizedSql countAll;

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

        char q = sqlType.quoteChar();
        String table = repositoryInformation.tableName();
        String base  = "SELECT * FROM " + q + table + q;

        this.selectAll   = ParameterizedSql.of(base);
        this.selectFirst = ParameterizedSql.of(base + " LIMIT 1");
        this.countAll    = ParameterizedSql.of("SELECT COUNT(*) FROM " + q + table + q);
    }

    public ParameterizedSql parseSelect(SelectQuery query, boolean first) {
        if (query == null) return first ? selectFirst : selectAll;

        char q = sqlType.quoteChar();
        String table = repositoryInformation.tableName();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(q).append(table).append(q);

        SqlConditionBuilder.BuiltCondition where = appendConditions(query, sql);
        appendSortingAndLimit(query, sql, first);

        return ParameterizedSql.of(sql.toString(), where.paramNames());
    }

    public ParameterizedSql parseCount(SelectQuery query) {
        if (query == null) return countAll;

        char q = sqlType.quoteChar();
        String table = repositoryInformation.tableName();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(q).append(table).append(q);

        SqlConditionBuilder.BuiltCondition where = appendConditions(query, sql);

        return ParameterizedSql.of(sql.toString(), where.paramNames());
    }

    public ParameterizedSql parseQueryIds(SelectQuery query, boolean first) {
        FieldModel<T> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Cannot find Id because it doesn't exist.");
        }

        char q = sqlType.quoteChar();
        String table  = repositoryInformation.tableName();
        String idName = primaryKey.columnName();

        if (query == null) {
            String sql = "SELECT " + idName + " FROM " + q + table + q + (first ? " LIMIT 1" : "");
            return ParameterizedSql.of(sql);
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(idName)
            .append(" FROM ").append(q).append(table).append(q);

        SqlConditionBuilder.BuiltCondition where = appendConditions(query, sql);
        appendSortingAndLimit(query, sql, first);

        return ParameterizedSql.of(sql.toString(), where.paramNames());
    }

    /**
     * Appends a WHERE clause to {@code sql} if the query has filters, and
     * returns the {@link SqlConditionBuilder.BuiltCondition} so callers can
     * include the param names in the resulting {@link ParameterizedSql}.
     * Returns an empty condition (no SQL appended, empty name list) when there
     * are no filters.
     */
    private SqlConditionBuilder.BuiltCondition appendConditions(SelectQuery query, StringBuilder sql) {
        if (query.filters().isEmpty()) {
            return new SqlConditionBuilder.BuiltCondition("", java.util.List.of());
        }

        SqlConditionBuilder.BuiltCondition where = conditionBuilder.buildConditionsFull(query.filters());
        sql.append(" WHERE ").append(where.sql());
        return where;
    }

    private void appendSortingAndLimit(SelectQuery query, StringBuilder sql, boolean first) {
        if (!query.sortOptions().isEmpty()) {
            sql.append(" ORDER BY ").append(sortBuilder.buildSortOptions(query.sortOptions()));
        }

        if (query.limit() != -1) {
            sql.append(" LIMIT ").append(query.limit());
        } else if (first) {
            sql.append(" LIMIT 1");
        }
    }
}