package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import org.jetbrains.annotations.NotNull;

public final class IndexSqlBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;

    public IndexSqlBuilder(QueryParseEngine.SQLType sqlType, RepositoryModel<T, ID> repositoryInformation) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
    }

    public String parseIndex(final @NotNull IndexOptions index) {
        String type = index.type() == IndexType.NORMAL ? "" : index.type().name() + " ";
        return "CREATE " + type + "INDEX " + sqlType.quoteChar() + index.indexName() + sqlType.quoteChar() + " ON "
            + sqlType.quoteChar() + repositoryInformation.tableName() + sqlType.quoteChar() + " (" + index.getJoinedFields() + ");";
    }
}
