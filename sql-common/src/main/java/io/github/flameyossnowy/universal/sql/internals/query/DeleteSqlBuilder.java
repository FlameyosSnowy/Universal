package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;

import java.util.StringJoiner;

public final class DeleteSqlBuilder<T, ID> {
    private final RepositoryModel<T, ID> repositoryInformation;
    private final SqlConditionBuilder<T, ID> conditionBuilder;

    public DeleteSqlBuilder(RepositoryModel<T, ID> repositoryInformation, SqlConditionBuilder<T, ID> conditionBuilder) {
        this.repositoryInformation = repositoryInformation;
        this.conditionBuilder = conditionBuilder;
    }

    public String parseDelete(DeleteQuery query) {
        if (query == null || query.filters().isEmpty()) {
            return "DELETE FROM " + repositoryInformation.tableName();
        }

        return "DELETE FROM " + repositoryInformation.tableName() + " WHERE " + conditionBuilder.buildConditions(query.filters());
    }

    public String parseDeleteEntity(Object value) {
        if (value == null) {
            throw new NullPointerException("Value must not be null");
        }

        if (value.getClass() != repositoryInformation.getEntityClass()) {
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.getEntityClass());
        }

        if (repositoryInformation.getPrimaryKey() == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        if (repositoryInformation.primaryKeys().size() > 1) {
            StringJoiner whereClause = new StringJoiner(" AND ");
            for (String keyField : repositoryInformation.primaryKeys()) {
                whereClause.add(keyField + " = ?");
            }
            return "DELETE FROM " + repositoryInformation.tableName() + " WHERE " + whereClause;
        }

        return "DELETE FROM " + repositoryInformation.tableName() + " WHERE " + repositoryInformation.getPrimaryKey().name() + " = ?";
    }
}
