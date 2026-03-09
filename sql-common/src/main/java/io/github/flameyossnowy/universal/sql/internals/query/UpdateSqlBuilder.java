package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.Collection;
import java.util.HashSet;
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

    public String parseUpdate(UpdateQuery query) {
        String tableName = repositoryInformation.tableName();
        String setClause = generateSetClause(query);

        return query.filters().isEmpty()
            ? "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + ";"
            : "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + " WHERE " + conditionBuilder.buildConditions(query.filters()) + ";";
    }

    public String parseUpdateFromEntity() {
        FieldModel<T> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        Set<String> versionColumns = resolveJsonVersionColumns();

        String tableName = repositoryInformation.tableName();
        String setClause = generateSetClauseFromEntity(versionColumns);

        if (repositoryInformation.primaryKeys().size() > 1) {
            StringJoiner whereClause = new StringJoiner(" AND ");
            for (String keyField : repositoryInformation.primaryKeys()) {
                whereClause.add(keyField + " = ?");
            }
            return "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + " WHERE " + whereClause + ";";
        }

        StringJoiner where = new StringJoiner(" AND ");
        where.add(primaryKey.columnName() + " = ?");
        for (String versionColumn : versionColumns) {
            where.add(versionColumn + " = ?");
        }
        return "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + " WHERE " + where + ";";
    }

    private static String generateSetClause(UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) {
            joiner.add(key + " = ?");
        }
        return joiner.toString();
    }

    private String generateSetClauseFromEntity(Set<String> versionColumns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (FieldModel<T> data : repositoryInformation.fields()) {
            if (Collection.class.isAssignableFrom(data.type())) continue;
            if (Map.class.isAssignableFrom(data.type())) continue;
            if (data.autoIncrement()) continue;
            if (data.id()) continue;

            String column = data.columnName();
            if (versionColumns.contains(column)) {
                joiner.add(column + " = " + column + " + 1");
                continue;
            }

            joiner.add(column + " = ?");
        }
        return joiner.toString();
    }

    private Set<String> resolveJsonVersionColumns() {
        Set<String> versionColumns = new HashSet<>(2);

        for (FieldModel<T> field : repositoryInformation.fields()) {
            if (field == null || !field.isJson() || !field.jsonVersioned()) {
                continue;
            }

            versionColumns.add(field.columnName() + "_version");
        }

        return versionColumns;
    }
}
