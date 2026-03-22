package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class DeleteSqlBuilder<T, ID> {
    private final RepositoryModel<T, ID> repositoryInformation;
    private final SqlConditionBuilder<T, ID> conditionBuilder;

    public DeleteSqlBuilder(RepositoryModel<T, ID> repositoryInformation, SqlConditionBuilder<T, ID> conditionBuilder) {
        this.repositoryInformation = repositoryInformation;
        this.conditionBuilder = conditionBuilder;
    }

    public ParameterizedSql parseDelete(DeleteQuery query) {
        String table = repositoryInformation.tableName();

        if (query == null || query.filters().isEmpty()) {
            return ParameterizedSql.of("DELETE FROM " + table);
        }

        SqlConditionBuilder.BuiltCondition where = conditionBuilder.buildConditionsFull(query.filters());
        return ParameterizedSql.of(
            "DELETE FROM " + table + " WHERE " + where.sql(),
            where.paramNames()
        );
    }

    public ParameterizedSql parseDeleteEntity(Object value) {
        if (value == null) throw new NullPointerException("Value must not be null");

        if (value.getClass() != repositoryInformation.getEntityClass()) {
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.getEntityClass());
        }

        if (repositoryInformation.getPrimaryKey() == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        String table = repositoryInformation.tableName();

        if (repositoryInformation.primaryKeys().size() > 1) {
            StringJoiner whereJoiner = new StringJoiner(" AND ");
            List<String> paramNames = new ArrayList<>();
            for (String keyField : repositoryInformation.primaryKeys()) {
                whereJoiner.add(keyField + " = ?");
                paramNames.add(keyField);
            }
            return ParameterizedSql.of("DELETE FROM " + table + " WHERE " + whereJoiner, paramNames);
        }

        String pkCol = repositoryInformation.getPrimaryKey().columnName();
        return ParameterizedSql.of(
            "DELETE FROM " + table + " WHERE " + pkCol + " = ?",
            List.of(pkCol)
        );
    }
}