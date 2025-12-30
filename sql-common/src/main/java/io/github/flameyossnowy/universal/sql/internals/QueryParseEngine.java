package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.api.resolver.ResolveWith;
import io.github.flameyossnowy.universal.api.resolver.SqlEncoding;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.DatabaseImplementation;

import io.github.flameyossnowy.universal.sql.query.SQLQueryValidator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public class QueryParseEngine {
    private final DatabaseImplementation sqlType;
    private final RepositoryInformation repositoryInformation;
    private final Map<String, String> queryMap;
    private final TypeResolverRegistry resolverRegistry;
    private final SQLConnectionProvider connectionProvider;

    private final String insert;

    public QueryParseEngine(DatabaseImplementation sqlType, final RepositoryInformation repositoryInformation, TypeResolverRegistry resolverRegistry, SQLConnectionProvider connectionProvider) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.resolverRegistry = resolverRegistry;
        this.connectionProvider = connectionProvider;
        this.queryMap = new ConcurrentHashMap<>(5);
        this.insert = parseInsert0();
    }

    private static @NotNull String getSelectKey(SelectQuery query, final boolean first) {
        if (query == null) {
            return first ? "QUERY:SELECT:FIRST" : "QUERY:SELECT";
        }
        return first ? "QUERY:SELECT:FIRST:" + query.hashCode() : ("QUERY:SELECT:" + query.hashCode());
    }

    public String parseIndex(final @NotNull IndexOptions index) {
        String type = index.type() == IndexType.NORMAL ? "" : index.type().name() + " ";
        return "CREATE " + type + "INDEX " + sqlType.quoteChar() + index.indexName() + sqlType.quoteChar() + " ON " + sqlType.quoteChar() + repositoryInformation.getRepositoryName() + sqlType.quoteChar() + " (" + index.getJoinedFields() + ");";
    }

    public @NotNull String parseSelect(SelectQuery query, boolean first, boolean ids) {
        String key = null;

        if (queryMap != null) {
            key = getSelectKey(query, first);
            String queryString = queryMap.get(key);
            if (queryString != null) return queryString;
        }

        String queryString = parseQuery(query, first);

        if (queryMap != null) queryMap.put(key, queryString);
        Logging.info("Parsed query for selecting: " + queryString);
        return queryString;
    }

    public @NotNull String parseSelect(SelectQuery query, boolean first) {
        return parseSelect(query, first, false);
    }

    private @NotNull String parseQuery(SelectQuery query, boolean first) {
        String tableName = repositoryInformation.getRepositoryName();

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

    public @NotNull String parseQueryIds(SelectQuery query, boolean first) {
        String tableName = repositoryInformation.getRepositoryName();

        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Cannot find Id because it doesn't exist.");
        }

        String idName = primaryKey.name();

        if (query == null) {
            return "SELECT " + idName  + " FROM " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + (first ? " LIMIT 1" : "");
        }
        StringBuilder sql = new StringBuilder("SELECT " + idName + " FROM " + sqlType.quoteChar())
                .append(tableName)
                .append(sqlType.quoteChar());

        appendConditions(query, sql);
        appendSortingAndLimit(query, sql, first);

        return sql.toString();
    }

    private static void appendConditions(@NotNull SelectQuery query, StringBuilder sql) {
        if (!query.filters().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildConditions(query.filters()));
        }
    }

    private static void appendSortingAndLimit(@NotNull SelectQuery query, StringBuilder sql, boolean first) {
        if (!query.sortOptions().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(buildSortOptions(query.sortOptions()));
        }

        if (query.limit() != -1) {
            sql.append(" LIMIT ").append(query.limit());
        } else if (first) {
            sql.append(" LIMIT 1");
        }
    }

    public @NotNull String parseDelete(DeleteQuery query) {
        if (query == null || query.filters().isEmpty()) {
            return "DELETE FROM " + repositoryInformation.getRepositoryName();
        }
        
        String key = "DELETE:" + query.hashCode();
        return queryMap.computeIfAbsent(key, k -> 
            "DELETE FROM " + repositoryInformation.getRepositoryName() + " WHERE " + buildConditions(query.filters())
        );
    }

    public @NotNull String parseDelete(Object value) {
        if (value == null) {
            throw new NullPointerException("Value must not be null");
        }

        if (value.getClass() != repositoryInformation.getType())
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.getType());

        if (repositoryInformation.getPrimaryKey() == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        String key = "DELETE:ENTITY:" + repositoryInformation.getType().getName();
        return queryMap.computeIfAbsent(key, k -> {
            if (repositoryInformation.hasCompositeKey()) {
                // For composite keys, we need to include all primary key fields in the WHERE clause
                StringJoiner whereClause = new StringJoiner(" AND ");
                for (FieldData<?> keyField : repositoryInformation.getPrimaryKeys()) {
                    whereClause.add(keyField.name() + " = ?");
                }
                return "DELETE FROM " + repositoryInformation.getRepositoryName() + " WHERE " + whereClause;
            } else {
                // For single primary key
                return "DELETE FROM " + repositoryInformation.getRepositoryName() + " WHERE " + repositoryInformation.getPrimaryKey().name() + " = ?";
            }
        });
    }

    public @NotNull String parseInsert() {
        Logging.info("Parsed query for inserting: " + insert);
        return insert;
    }

    public @NotNull String parseInsert0() {
        StringJoiner columnJoiner = new StringJoiner(", ");

        StringBuilder queryBuilder = new StringBuilder("INSERT INTO " + sqlType.quoteChar());
        queryBuilder.append(repositoryInformation.getRepositoryName()).append(sqlType.quoteChar()).append(" (");

        StringJoiner joiner = new StringJoiner(", ");
        for (FieldData<?> data : repositoryInformation.getFields()) {
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

    public @NotNull String parseUpdate(UpdateQuery query) {
        String key = "UPDATE:" + query.hashCode();
        return queryMap.computeIfAbsent(key, k -> {
            String tableName = repositoryInformation.getRepositoryName();
            String setClause = generateSetClause(query);

            return query.filters().isEmpty()
                    ? "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + ";"
                    : "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + " WHERE " + buildConditions(query.filters()) + ";";
        });
    }

    public @NotNull String parseUpdateFromEntity() {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        String key = "UPDATE:ENTITY:" + repositoryInformation.getType().getName();
        return queryMap.computeIfAbsent(key, k -> {
            String tableName = repositoryInformation.getRepositoryName();
            String setClause = generateSetClauseFromEntity();

            if (repositoryInformation.hasCompositeKey()) {
                // For composite keys, include all primary key fields in the WHERE clause
                StringJoiner whereClause = new StringJoiner(" AND ");
                for (FieldData<?> keyField : repositoryInformation.getPrimaryKeys()) {
                    whereClause.add(keyField.name() + " = ?");
                }
                return "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + " WHERE " + whereClause + ";";
            } else {
                // For single primary key
                return "UPDATE " + sqlType.quoteChar() + tableName + sqlType.quoteChar() + " SET " + setClause + " WHERE " + primaryKey.name() + " = ?;";
            }
        });
    }

    private String generateSetClauseFromEntity() {
        StringJoiner joiner = new StringJoiner(", ");
        for (FieldData<?> data : repositoryInformation.getFields()) {
            if (Collection.class.isAssignableFrom(data.type())) continue;
            if (Map.class.isAssignableFrom(data.type())) continue;
            if (data.autoIncrement()) continue;
            if (data.primary()) continue;
            joiner.add(data.name() + " = ?");
        }
        return joiner.toString();
    }

    /*
     * |--------------|
     * | Repositories |
     * |--------------|
     */

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public @NotNull String parseRepository(boolean ifNotExists) {
        Logging.deepInfo("Starting repository parse: " + repositoryInformation.getRepositoryName());
        Logging.deepInfo("IF NOT EXISTS = " + ifNotExists);

        Set<FieldData<?>> childTableQueue = new HashSet<>(4);

        String tableName = repositoryInformation.getRepositoryName();
        String ddlPrefix =
            "CREATE TABLE " + (ifNotExists ? "IF NOT EXISTS " : "") +
                sqlType.quoteChar() + tableName + sqlType.quoteChar();

        Logging.deepInfo("DDL prefix: " + ddlPrefix);

        StringJoiner joiner = new StringJoiner(", ", ddlPrefix + " (", ");");

        generateColumns(joiner, childTableQueue);

        String classConstraints = processClassLevelConstraints();
        if (!classConstraints.isEmpty()) {
            Logging.deepInfo("Adding class-level constraints: " + classConstraints);
            joiner.add(classConstraints);
        }

        String finalQuery = joiner.toString();
        Logging.deepInfo("Final CREATE TABLE query:\n" + finalQuery);

        String query = createTable(
            finalQuery,
            "Failed to create main repository table: ",
            tableName
        );

        if (!childTableQueue.isEmpty()) {
            Logging.deepInfo("Creating " + childTableQueue.size() + " child tables");
        }

        for (FieldData<?> data : childTableQueue) {
            Logging.deepInfo("Creating child table for field: " + data.name());
            createChildTable(data);
        }

        return query;
    }

    private String createTable(String query, String errorMessage, String repositoryName) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connectionProvider.prepareStatement(query, connection)) {
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(errorMessage + repositoryName, e);
        }
        return query;
    }

    private String processClassLevelConstraints() {
        Constraint[] constraints = repositoryInformation.getConstraints();
        if (constraints.length == 0) {
            Logging.deepInfo("No class-level constraints found");
            return "";
        }

        Logging.deepInfo("Processing " + constraints.length + " class-level constraints");

        StringJoiner joiner = new StringJoiner(", ");
        for (Constraint constraint : constraints) {
            Logging.deepInfo("Processing constraint: " + constraint.name());

            StringJoiner checkConditionsJoiner = new StringJoiner(" AND ");
            StringJoiner uniqueFieldsJoiner = new StringJoiner(", ");

            for (String fieldName : constraint.fields()) {
                FieldData<?> fieldData = repositoryInformation.getField(fieldName);
                if (fieldData == null) {
                    Logging.deepInfo("Constraint field not found: " + fieldName);
                    continue;
                }

                if (fieldData.condition() != null) {
                    checkConditionsJoiner.add(fieldData.condition().value());
                }
                if (fieldData.unique()) {
                    uniqueFieldsJoiner.add(fieldName);
                }
            }

            if (checkConditionsJoiner.length() > 0) {
                String check = "CONSTRAINT " + constraint.name() +
                    " CHECK (" + checkConditionsJoiner + ")";
                Logging.deepInfo("Generated CHECK constraint: " + check);
                joiner.add(check);
            }

            if (uniqueFieldsJoiner.length() > 0) {
                String unique = "CONSTRAINT " + constraint.name() +
                    " UNIQUE (" + uniqueFieldsJoiner + ")";
                Logging.deepInfo("Generated UNIQUE constraint: " + unique);
                joiner.add(unique);
            }
        }

        return joiner.toString();
    }

    @Contract(pure = true)
    private void generateColumns(final StringJoiner joiner, Set<FieldData<?>> childTableQueue) {
        Logging.deepInfo("Generating columns for repository: " +
            repositoryInformation.getRepositoryName());

        StringJoiner primaryKeysJoiner = new StringJoiner(", ");
        StringJoiner relationshipsJoiner = new StringJoiner(", ");

        for (FieldData<?> data : repositoryInformation.getFields()) {
            Logging.deepInfo("----");
            Logging.deepInfo("Processing field: " + data.name());

            generateColumn(
                joiner,
                data,
                data.type(),
                new StringBuilder(32),
                data.name(),
                data.unique(),
                data.primary(),
                primaryKeysJoiner,
                relationshipsJoiner,
                childTableQueue
            );
        }

        if (primaryKeysJoiner.length() > 0) {
            String pk = "PRIMARY KEY (" + primaryKeysJoiner + ")";
            Logging.deepInfo("Primary key clause: " + pk);
            joiner.add(pk);
        }

        if (relationshipsJoiner.length() > 0) {
            Logging.deepInfo("Relationship clauses: " + relationshipsJoiner);
            joiner.add(relationshipsJoiner.toString());
        }
    }

    private void generateColumn(
        StringJoiner joiner,
        FieldData<?> data,
        Class<?> type,
        StringBuilder fieldBuilder,
        String name,
        boolean unique,
        boolean primaryKey,
        StringJoiner primaryKeysJoiner,
        StringJoiner relationshipsJoiner,
        Set<FieldData<?>> childTableQueue
    ) {
        if (data.isRelationship()) {
            return;
        }

        // handle collections
        if (Collection.class.isAssignableFrom(type)) {
            if (!sqlType.supportsArrays()) {
                childTableQueue.add(data);
                return;
            }

            Class<?> genericType = data.elementType() != null ? data.elementType() : Object.class;
            String resolvedType = resolverRegistry.getType(genericType.arrayType(), data.binary() ? SqlEncoding.BINARY : SqlEncoding.VISUAL) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        // handle maps
        if (Map.class.isAssignableFrom(type)) {
            childTableQueue.add(data);
            return;
        }

        // handle arrays
        if (type.isArray()) {
            if (!sqlType.supportsArrays()) {
                childTableQueue.add(data);
                return;
            }
            String resolvedType = resolverRegistry.getType(type.getComponentType(), data.binary() ? SqlEncoding.BINARY : SqlEncoding.VISUAL) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        String resolvedType = resolverRegistry.getType(type, data.binary() ? SqlEncoding.BINARY : SqlEncoding.VISUAL);
        if (resolvedType == null) {
            TypeResolver<?> newResolver = createResolver(data);
            resolvedType = resolverRegistry.getType(newResolver);
        }

        RepositoryInformation metadata;
        if (resolvedType == null && (metadata = RepositoryMetadata.getMetadata(type)) != null) {
            FieldData<?> metadataPrimaryKey = metadata.getPrimaryKey();
            Objects.requireNonNull(metadataPrimaryKey, "Primary key must not be null");
            resolvedType = resolverRegistry.getType(metadataPrimaryKey.type(), data.binary() ? SqlEncoding.BINARY : SqlEncoding.VISUAL);
        }

        Objects.requireNonNull(resolvedType, "Unsupported type: " + type);

        appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
    }

    private void appendColumn(
        @NotNull StringJoiner joiner,
        FieldData<?> data,
        @NotNull StringBuilder fieldBuilder,
        String name,
        boolean unique,
        boolean primaryKey,
        StringJoiner primaryKeysJoiner,
        StringJoiner relationshipsJoiner,
        String resolvedType
    ) {
        fieldBuilder.append(name).append(' ').append(resolvedType);
        addColumnMetaData(data, fieldBuilder, unique);

        String columnSql = fieldBuilder.toString();
        Logging.deepInfo("Generated column SQL: " + columnSql);

        joiner.add(columnSql);

        if (primaryKey) {
            Logging.deepInfo("Marked as PRIMARY KEY: " + name);
            primaryKeysJoiner.add(name);
        }

        addPotentialManyToOne(data, name, relationshipsJoiner);
    }

    private void addColumnMetaData(@NotNull FieldData<?> data, StringBuilder fieldBuilder, boolean unique) {
        if (data.nonNull()) fieldBuilder.append(" NOT NULL");
        if (data.autoIncrement()) fieldBuilder.append(" ").append(sqlType.autoIncrementKeyword());
        if (data.condition() != null) fieldBuilder.append(" CHECK (").append(data.condition().value()).append(")");
        if (unique) fieldBuilder.append(" UNIQUE");
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable TypeResolver<T> createResolver(final @NotNull FieldData<?> data) {
        TypeResolver<?> resolver = parseNewResolver(data);
        return (TypeResolver<T>) resolver;
    }

    @SuppressWarnings("unchecked")
    private @Nullable TypeResolver<Object> parseNewResolver(final @NotNull FieldData<?> data) {
        ResolveWith annotation = data.resolveWith();
        if (annotation == null) {
            return null;
        }

        if (!TypeResolver.class.isAssignableFrom(annotation.value())) {
            throw new IllegalArgumentException("Annotation value must be an SQLValueTypeResolver: " + annotation.value());
        }
        try {
            TypeResolver<Object> newResolver = (TypeResolver<Object>) annotation.value().getDeclaredConstructor().newInstance();
            resolverRegistry.register(newResolver);
            return newResolver;
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void createChildTable(FieldData<?> data) {
        String childTableName = repositoryInformation.getRepositoryName() + "_" + data.name();
        Class<?> elementType = (data.elementType() != null) ? data.elementType() : Object.class;

        String elementSqlType = resolverRegistry.getType(elementType);
        String idSqlType = resolverRegistry.getType(repositoryInformation.getPrimaryKey().type());

        StringBuilder sb = new StringBuilder(128);
        sb.append("CREATE TABLE IF NOT EXISTS ").append(sqlType.quoteChar()).append(childTableName).append(sqlType.quoteChar())
            .append(" (\n")
            .append("  ").append(sqlType.quoteChar()).append("id").append(sqlType.quoteChar()).append(" ").append(idSqlType).append(" NOT NULL,\n");

        if (Collection.class.isAssignableFrom(data.type())) {
            sb.append("  ").append(sqlType.quoteChar()).append("value").append(sqlType.quoteChar()).append(" ").append(elementSqlType).append(" NOT NULL,\n");
        } else if (Map.class.isAssignableFrom(data.type())) {
            sb.append("  ").append(sqlType.quoteChar()).append("map_key").append(sqlType.quoteChar()).append(" ").append(resolverRegistry.getType(data.mapKeyType())).append(" NOT NULL,\n")
                .append("  ").append(sqlType.quoteChar()).append("map_value").append(sqlType.quoteChar()).append(" ").append(elementSqlType).append(" NOT NULL,\n");
        }

        sb.append("  FOREIGN KEY (").append(sqlType.quoteChar()).append("id").append(sqlType.quoteChar()).append(") REFERENCES ")
            .append(sqlType.quoteChar()).append(repositoryInformation.getRepositoryName()).append(sqlType.quoteChar()).append("(")
            .append(sqlType.quoteChar()).append("id").append(sqlType.quoteChar()).append(") ON DELETE CASCADE ON UPDATE CASCADE\n")
            .append(");");

        createTable(sb.toString(), "Failed to create child table: ", childTableName);
    }

    private static void addPotentialManyToOne(@NotNull FieldData<?> data, String name, StringJoiner relationshipsJoiner) {
        if (data.manyToOne() == null) return;
        RepositoryInformation parent = RepositoryMetadata.getMetadata(data.type());
        Objects.requireNonNull(parent, "Parent should not be null");

        FieldData<?> primaryKey = parent.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Parent should have a primary key");
        }

        String table = parent.getRepositoryName();

        String primaryKeyName = primaryKey.name();
        String onDelete = data.onDelete() != null ? data.onDelete().value().name() : "";
        String onUpdate = data.onUpdate() != null ? data.onUpdate().value().name() : "";

        // optimization: we counted 38 chars in the string builder, and we added the lengths of the unknown strings.
        // this should be enough to avoid array copies.
        StringBuilder fkBuilder = new StringBuilder(38 + name.length() + table.length() + primaryKeyName.length() + onDelete.length() + onUpdate.length());
        fkBuilder.append("FOREIGN KEY (").append(name).append(") REFERENCES ").append(table).append('(').append(primaryKeyName).append(')');
        if (data.onDelete() != null) fkBuilder.append(" ON DELETE ").append(onDelete);
        if (data.onUpdate() != null) fkBuilder.append(" ON UPDATE ").append(onUpdate);
        relationshipsJoiner.add(fkBuilder);
    }

    private static @NotNull String generateSetClause(@NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) joiner.add(key + " = ?");
        return joiner.toString();
    }

    private static String buildConditions(@NotNull Iterable<SelectOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption filter : filters) {
            // Handle IN clause specially
            if ("IN".equalsIgnoreCase(filter.operator())) {
                Object value = filter.value();
                if (value instanceof Collection<?> list) {
                    String placeholders = String.join(", ", Collections.nCopies(list.size(), "?"));
                    joiner.add(filter.option() + " IN (" + placeholders + ")");
                } else {
                    joiner.add(filter.option() + " IN (?)");
                }
            } else {
                joiner.add(filter.option() + " " + filter.operator() + " ?");
            }
        }
        return joiner.toString();
    }

    private static String buildSortOptions(@NotNull Iterable<SortOption> sortOptions) {
        StringJoiner joiner = new StringJoiner(", ");
        for (SortOption sortOption : sortOptions) joiner.add(sortOption.field() + " " + (sortOption.order() == SortOrder.ASCENDING ? "ASC" : "DESC"));
        return joiner.toString();
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