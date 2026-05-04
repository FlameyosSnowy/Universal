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
import io.github.flameyossnowy.universal.sql.internals.query.ParameterizedSql;
import io.github.flameyossnowy.universal.sql.internals.query.QueryStringCache;
import io.github.flameyossnowy.universal.sql.internals.query.RepositoryDdlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.SelectSqlBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.SqlConditionBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.SqlSortBuilder;
import io.github.flameyossnowy.universal.sql.internals.query.UpdateSqlBuilder;

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

    /** Eagerly-built INSERT so {@link #parseInsert()} is allocation-free. */
    private final ParameterizedSql insert;

    // Cached entity-update SQL (same shape every time for a given entity class).
    private final ParameterizedSql updateFromEntity;

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
        this.updateFromEntity = updateSqlBuilder.parseUpdateFromEntity();
    }

    private static @NotNull String getSelectKey(SelectQuery query, final boolean first) {
        if (query == null) return first ? "QUERY:SELECT:FIRST" : "QUERY:SELECT";
        return first ? "QUERY:SELECT:FIRST:" + query.hashCode() : "QUERY:SELECT:" + query.hashCode();
    }

    private static @NotNull String getCountKey(SelectQuery query) {
        return query == null ? "QUERY:COUNT" : "QUERY:COUNT:" + query.hashCode();
    }

    public String parseIndex(final @NotNull IndexOptions index) {
        return indexSqlBuilder.parseIndex(index);
    }

    public @NotNull ParameterizedSql parseSelect(SelectQuery query, boolean first) {
        String key = getSelectKey(query, first);
        ParameterizedSql cached = queryMap.get(key);
        if (cached != null) return cached;

        ParameterizedSql sql = selectSqlBuilder.parseSelect(query, first);
        queryMap.put(key, sql);
        Logging.info(() -> "Parsed query for selecting: " + sql);
        return sql;
    }

    public @NotNull ParameterizedSql parseCount(SelectQuery query) {
        String key = getCountKey(query);
        ParameterizedSql cached = queryMap.get(key);
        if (cached != null) return cached;

        ParameterizedSql sql = selectSqlBuilder.parseCount(query);
        queryMap.put(key, sql);
        Logging.info(() -> "Parsed query for count: " + sql);
        return sql;
    }

    public @NotNull ParameterizedSql parseQueryIds(SelectQuery query, boolean first) {
        return selectSqlBuilder.parseQueryIds(query, first);
    }

    public @NotNull ParameterizedSql parseDelete(DeleteQuery query) {
        return deleteSqlBuilder.parseDelete(query);
    }

    public @NotNull ParameterizedSql parseDelete(Object value) {
        if (value == null) throw new NullPointerException("Value must not be null");

        if (value.getClass() != repositoryInformation.getEntityClass()) {
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.getEntityClass());
        }

        if (repositoryInformation.getPrimaryKey() == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        return deleteSqlBuilder.parseDeleteEntity(value);
    }

    public @NotNull ParameterizedSql parseInsert() {
        Logging.deepInfo(() -> "Parsed query for insert: " + insert);
        return insert;
    }

    public @NotNull ParameterizedSql parseUpdate(@NotNull UpdateQuery query) {
        return updateSqlBuilder.parseUpdate(query);
    }

    public @NotNull ParameterizedSql parseUpdateFromEntity() {
        return updateFromEntity;
    }

    public @NotNull String parseRepository(boolean ifNotExists) {
        return repositoryDdlBuilder.parseRepository(ifNotExists);
    }

    public enum SQLType implements DatabaseImplementation, io.github.flameyossnowy.universal.api.resolver.DatabaseDialect {
        MYSQL("mysql", "MySQL", "AUTO_INCREMENT", false, '`'),
        SQLITE("sqlite", "SQLite", "AUTOINCREMENT", false, '"'),
        POSTGRESQL("postgresql", "PostgreSQL", "GENERATED ALWAYS AS IDENTITY", true, '"');

        private final String identifier;
        private final boolean supportsArrays;
        private final String name;
        private final String autoIncrementKeyword;
        private final char quotesChar;

        SQLType(String identifier, String name, String autoIncrementKeyword, boolean supportsArrays, char quotesChar) {
            this.identifier = identifier;
            this.supportsArrays = supportsArrays;
            this.name = name;
            this.autoIncrementKeyword = autoIncrementKeyword;
            this.quotesChar = quotesChar;
        }

        @Override public String getName()              { return name; }
        @Override public String autoIncrementKeyword() { return autoIncrementKeyword; }
        @Override public char quoteChar()              { return quotesChar; }
        @Override public boolean supportsArrays()      { return supportsArrays; }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        public SQLQueryValidator.SQLDialect getDialect() {
            return switch (this) {
                case MYSQL      -> SQLQueryValidator.SQLDialect.MYSQL;
                case POSTGRESQL -> SQLQueryValidator.SQLDialect.POSTGRESQL;
                case SQLITE     -> SQLQueryValidator.SQLDialect.SQLITE;
            };
        }
    }
}