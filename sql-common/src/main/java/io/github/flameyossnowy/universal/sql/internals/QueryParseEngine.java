package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.DatabaseImplementation;
import io.github.flameyossnowy.universal.sql.query.SQLQueryValidator;
import io.github.flameyossnowy.universal.sql.internals.query.DeleteSqlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.IndexSqlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.InsertSqlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.QueryStringCache;
import io.github.flameyossnowy.universal.sql.internals.query.RepositoryDdlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.SelectSqlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.SqlConditionBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.SqlSortBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.UpdateSqlBuilder;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class QueryParseEngine<T, ID> {
    private final SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;
    private final QueryStringCache queryMap;
    private final SqlConditionBuilder<T, ID> conditionBuilder;
    private final SelectSqlBuilder<T, ID> selectSqlBuilder;
    private final InsertSqlBuilder<T, ID> insertSqlBuilder;
    private final UpdateSqlBuilder<T, ID> updateSqlBuilder;
    private final DeleteSqlBuilder<T, ID> deleteSqlBuilder;
    private final IndexSqlBuilder<T, ID> indexSqlBuilder;
    private final RepositoryDdlBuilder<T, ID> repositoryDdlBuilder;

    private final String insert;

    public QueryParseEngine(SQLType sqlType, final RepositoryModel<T, ID> repositoryInformation, TypeResolverRegistry resolverRegistry, SQLConnectionProvider connectionProvider) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        Objects.requireNonNull(resolverRegistry);
        Objects.requireNonNull(connectionProvider);

        this.queryMap = new QueryStringCache(5);

        this.conditionBuilder = new SqlConditionBuilder<>(sqlType, repositoryInformation);
        this.selectSqlBuilder = new SelectSqlBuilder<>(
            sqlType,
            repositoryInformation,
            conditionBuilder,
            new SqlSortBuilder()
        );
        this.insertSqlBuilder = new InsertSqlBuilder<>(sqlType, repositoryInformation);
        this.updateSqlBuilder = new UpdateSqlBuilder<>(sqlType, repositoryInformation, conditionBuilder);
        this.deleteSqlBuilder = new DeleteSqlBuilder<>(repositoryInformation, conditionBuilder);
        this.indexSqlBuilder = new IndexSqlBuilder<>(sqlType, repositoryInformation);
        this.repositoryDdlBuilder = new RepositoryDdlBuilder<>(sqlType, repositoryInformation, resolverRegistry, connectionProvider);

        this.insert = insertSqlBuilder.parseInsert();
    }

    private static @NotNull String getSelectKey(SelectQuery query, final boolean first) {
        if (query == null) {
            return first ? "QUERY:SELECT:FIRST" : "QUERY:SELECT";
        }
        return first ? "QUERY:SELECT:FIRST:" + query.hashCode() : ("QUERY:SELECT:" + query.hashCode());
    }

    private static @NotNull String getCountKey(SelectQuery query) {
        if (query == null) {
            return "QUERY:COUNT";
        }
        return "QUERY:COUNT:" + query.hashCode();
    }

    public String parseIndex(final @NotNull IndexOptions index) {
        return indexSqlBuilder.parseIndex(index);
    }

    public @NotNull String parseSelect(SelectQuery query, boolean first) {
        String key = null;

        if (queryMap != null) {
            key = getSelectKey(query, first);
            String queryString = queryMap.get(key);
            if (queryString != null) return queryString;
        }

        String queryString = selectSqlBuilder.parseSelect(query, first);

        if (queryMap != null) queryMap.put(key, queryString);
        Logging.info(() -> "Parsed query for selecting: " + queryString);
        return queryString;
    }

    public @NotNull String parseCount(SelectQuery query) {
        String key = null;

        if (queryMap != null) {
            key = getCountKey(query);
            String queryString = queryMap.get(key);
            if (queryString != null) return queryString;
        }

        String queryString = selectSqlBuilder.parseCount(query);

        if (queryMap != null) queryMap.put(key, queryString);
        Logging.info(() -> "Parsed query for count: " + queryString);
        return queryString;
    }

    public @NotNull String parseQueryIds(SelectQuery query, boolean first) {
        return selectSqlBuilder.parseQueryIds(query, first);
    }

    public @NotNull String parseDelete(DeleteQuery query) {
        if (query == null || query.filters().isEmpty()) {
            return deleteSqlBuilder.parseDelete(query);
        }

        String key = "DELETE:" + query.hashCode();
        return queryMap.computeIfAbsent(key, k -> deleteSqlBuilder.parseDelete(query));
    }

    public @NotNull String parseDelete(Object value) {
        if (value == null) {
            throw new NullPointerException("Value must not be null");
        }

        if (value.getClass() != repositoryInformation.getEntityClass())
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.getEntityClass());

        if (repositoryInformation.getPrimaryKey() == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        String key = "DELETE:ENTITY:" + repositoryInformation.entitySimpleName();
        return queryMap.computeIfAbsent(key, k -> deleteSqlBuilder.parseDeleteEntity(value));
    }

    public @NotNull String parseInsert() {
        Logging.info(() -> "Parsed query for inserting: " + insert);
        return insert;
    }

    public @NotNull String parseUpdate(@NotNull UpdateQuery query) {
        String key = "UPDATE:" + query.hashCode();
        return queryMap.computeIfAbsent(key, k -> updateSqlBuilder.parseUpdate(query));
    }

    public @NotNull String parseUpdateFromEntity() {
        String key = "UPDATE:ENTITY:" + repositoryInformation.entityQualifiedName();
        return queryMap.computeIfAbsent(key, k -> updateSqlBuilder.parseUpdateFromEntity());
    }

    /*
     * |--------------|
     * | Repositories |
     * |--------------|
     */

    public @NotNull String parseRepository(boolean ifNotExists) {
        return repositoryDdlBuilder.parseRepository(ifNotExists);
    }

    public enum SQLType implements DatabaseImplementation {
        MYSQL("MySQL", "AUTO_INCREMENT", false, '`'),
        SQLITE("SQLite", "AUTOINCREMENT", false, '"'),
        POSTGRESQL("PostgreSQL", "GENERATED ALWAYS AS IDENTITY", true, '"');

        private final boolean supportsArrays;
        private final String name;
        private final String autoIncrementKeyword;
        private final char quotesChar;

        SQLType(String name, String autoIncrementKeyword, boolean supportsArrays, char quotesChar) {
            this.supportsArrays = supportsArrays;
            this.name = name;
            this.autoIncrementKeyword = autoIncrementKeyword;
            this.quotesChar = quotesChar;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String autoIncrementKeyword() {
            return autoIncrementKeyword;
        }

        @Override
        public char quoteChar() {
            return quotesChar;
        }

        @Override
        public boolean supportsArrays() {
            return supportsArrays;
        }

        public SQLQueryValidator.SQLDialect getDialect() {
            return switch (this) {
                case MYSQL -> SQLQueryValidator.SQLDialect.MYSQL;
                case POSTGRESQL -> SQLQueryValidator.SQLDialect.POSTGRESQL;
                case SQLITE -> SQLQueryValidator.SQLDialect.SQLITE;
            };
        }
    }
}