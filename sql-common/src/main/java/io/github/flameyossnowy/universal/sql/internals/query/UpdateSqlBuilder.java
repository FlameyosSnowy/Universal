package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public final class UpdateSqlBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;
    private final SqlConditionBuilder<T, ID> conditionBuilder;

    public UpdateSqlBuilder(
        QueryParseEngine.SQLType sqlType,
        RepositoryModel<T, ID> repositoryInformation,
        SqlConditionBuilder<T, ID> conditionBuilder
    ) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.conditionBuilder = conditionBuilder;
    }

    public ParameterizedSql parseUpdate(UpdateQuery query) {
        char q = sqlType.quoteChar();
        String tableName = repositoryInformation.tableName();

        List<String> paramNames = new ArrayList<>();

        StringJoiner setJoiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) {
            setJoiner.add(key + " = ?");
            paramNames.add(key);
        }
        String setClause = setJoiner.toString();

        if (query.filters().isEmpty()) {
            String sql = "UPDATE " + q + tableName + q + " SET " + setClause + ";";
            return ParameterizedSql.of(sql, paramNames);
        }

        SqlConditionBuilder.BuiltCondition where = conditionBuilder.buildConditionsFull(query.filters());
        paramNames.addAll(where.paramNames());

        String sql = "UPDATE " + q + tableName + q + " SET " + setClause + " WHERE " + where.sql() + ";";
        return ParameterizedSql.of(sql, paramNames);
    }

    public ParameterizedSql parseUpdateFromEntity() {
        FieldModel<T> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        char q = sqlType.quoteChar();
        String tableName = repositoryInformation.tableName();
        Set<String> versionColumns = resolveJsonVersionColumns();

        List<String> paramNames = new ArrayList<>();

        // SET clause
        StringJoiner setJoiner = new StringJoiner(", ");
        for (FieldModel<T> data : repositoryInformation.fields()) {
            if (Collection.class.isAssignableFrom(data.type())) continue;
            if (Map.class.isAssignableFrom(data.type())) continue;
            if (data.autoIncrement()) continue;
            if (data.id()) continue;

            String column = data.columnName();
            if (versionColumns.contains(column)) {
                setJoiner.add(column + " = " + column + " + 1");
                continue;
            }

            setJoiner.add(column + " = ?");
            paramNames.add(column);
        }

        // WHERE clause
        if (repositoryInformation.primaryKeys().size() > 1) {
            StringJoiner whereJoiner = new StringJoiner(" AND ");
            for (String keyField : repositoryInformation.primaryKeys()) {
                whereJoiner.add(keyField + " = ?");
                paramNames.add(keyField);
            }
            String sql = "UPDATE " + q + tableName + q + " SET " + setJoiner + " WHERE " + whereJoiner + ";";
            return ParameterizedSql.of(sql, paramNames);
        }

        StringJoiner whereJoiner = new StringJoiner(" AND ");
        whereJoiner.add(primaryKey.columnName() + " = ?");
        paramNames.add(primaryKey.columnName());

        for (String versionColumn : versionColumns) {
            whereJoiner.add(versionColumn + " = ?");
            paramNames.add(versionColumn);
        }

        String sql = "UPDATE " + q + tableName + q + " SET " + setJoiner + " WHERE " + whereJoiner + ";";
        return ParameterizedSql.of(sql, paramNames);
    }

    private Set<String> resolveJsonVersionColumns() {
        Set<String> versionColumns = new HashSet<>(2);
        for (FieldModel<T> field : repositoryInformation.fields()) {
            if (field == null || !field.isJson() || !field.jsonVersioned()) continue;
            versionColumns.add(field.columnName() + "_version");
        }
        return versionColumns;
    }
}