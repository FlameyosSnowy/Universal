package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.api.resolver.ResolveWith;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.DatabaseImplementation;

import io.github.flameyossnowy.universal.sql.query.SQLQueryValidator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
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

    private final String insert;

    public QueryParseEngine(DatabaseImplementation sqlType, final RepositoryInformation repositoryInformation, TypeResolverRegistry resolverRegistry) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.resolverRegistry = resolverRegistry;
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
        return String.format("CREATE %sINDEX %s%s%s ON %s%s%s (%s);", type,
            sqlType.quoteChar(),
            index.indexName(),
            sqlType.quoteChar(),
            sqlType.quoteChar(),
            repositoryInformation.getRepositoryName(),
            sqlType.quoteChar(),
            index.getJoinedFields());
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

    private @NotNull String parseQueryIds(SelectQuery query, boolean first) {
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
                    ? String.format("UPDATE %s%s%s SET %s;", sqlType.quoteChar(), tableName, sqlType.quoteChar(), setClause)
                    : String.format("UPDATE %s%s%s SET %s WHERE %s;", sqlType.quoteChar(), tableName, sqlType.quoteChar(), setClause, buildConditions(query.filters()));
        });
    }

    public @NotNull String parseUpdateFromEntity() {
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
                return String.format("UPDATE %s%s%s SET %s WHERE %s;", sqlType.quoteChar(), tableName, sqlType.quoteChar(), setClause, whereClause);
            } else {
                // For single primary key
                return String.format("UPDATE %s%s%s SET %s WHERE %s = ?;", sqlType.quoteChar(), tableName, sqlType.quoteChar(), setClause, repositoryInformation.getPrimaryKey().name());
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

    public @NotNull String parseRepository(boolean ifNotExists) {
        StringJoiner joiner = new StringJoiner(", ",
            "CREATE TABLE " +
                    (ifNotExists ? "IF NOT EXISTS " + sqlType.quoteChar() : sqlType.quoteChar())
            + repositoryInformation.getRepositoryName() + sqlType.quoteChar() + " (",
            ");"
        );

        Constraint[] constraints = repositoryInformation.getConstraints();
        Set<String> constrainedFields = constraints.length > 0 ? new HashSet<>(constraints.length) : Collections.emptySet();
        if (constraints.length > 0) addConstraints(constrainedFields);

        generateColumns(joiner);

        String classConstraints = processClassLevelConstraints();
        if (!classConstraints.isEmpty()) {
            joiner.add(classConstraints);
        }

        return joiner.toString();
    }

    private void addConstraints(final Set<String> constrainedFields) {
        for (Constraint c : repositoryInformation.getConstraints()) Collections.addAll(constrainedFields, c.fields());
    }

    private String processClassLevelConstraints() {
        if (repositoryInformation.getConstraints().length == 0) return "";

        StringJoiner joiner = new StringJoiner(", ");
        for (Constraint constraint : repositoryInformation.getConstraints()) {
            String[] fields = constraint.fields();
            if (fields.length == 0) continue;

            StringJoiner checkConditionsJoiner = new StringJoiner(" AND ");
            StringJoiner uniqueFieldsJoiner = new StringJoiner(", ");

            for (String fieldName : fields) {
                FieldData<?> fieldData = repositoryInformation.getField(fieldName);
                if (fieldData == null) continue;
                if (fieldData.condition() != null) {
                    checkConditionsJoiner.add(fieldData.condition().value());
                }
                if (fieldData.unique()) {
                    uniqueFieldsJoiner.add(fieldName);
                }
            }
            if (checkConditionsJoiner.length() > 0) {
                joiner.add("CONSTRAINT " + constraint.name() + " CHECK (" + checkConditionsJoiner + ")");
            }
            if (uniqueFieldsJoiner.length() > 0) {
                joiner.add("CONSTRAINT " + constraint.name() + " UNIQUE (" + uniqueFieldsJoiner + ")");
            }
        }
        return joiner.toString();
    }

    @Contract(pure = true)
    private void generateColumns(final StringJoiner joiner) {
        StringJoiner primaryKeysJoiner = new StringJoiner(", ");
        StringJoiner relationshipsJoiner = new StringJoiner(", ");

        for (FieldData<?> data : repositoryInformation.getFields()) {
            StringBuilder fieldBuilder = new StringBuilder(32);
            final Class<?> type = data.type();
            final boolean primaryKey = data.primary();
            final boolean unique = data.unique();
            final String name = data.name();

            Logging.deepInfo("Processing field: " + name);
            Logging.deepInfo("Field type: " + type);

            generateColumn(joiner, data, type, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner);
        }

        String pkClause = primaryKeysJoiner.toString();
        if (!pkClause.isEmpty()) {
            joiner.add("PRIMARY KEY (" + pkClause + ")");
        }

        String relationshipClause = relationshipsJoiner.toString();
        if (!relationshipClause.isEmpty()) {
            joiner.add(relationshipClause);
        }
    }

    protected void generateColumn(StringJoiner joiner, FieldData<?> data, Class<?> type, StringBuilder fieldBuilder, String name, boolean unique, boolean primaryKey, StringJoiner primaryKeysJoiner, StringJoiner relationshipsJoiner) {
        if (Collection.class.isAssignableFrom(type)) {
            if (!sqlType.supportsArrays()) return;  // not ignored, just handled differently and somewhere else, likely in DatabaseRelationshipHandler

            // get the generic type
            Class<?> genericType = (Class<?>) ((ParameterizedType) type.getGenericSuperclass()).getActualTypeArguments()[0];

            String resolvedType = resolverRegistry.getType(genericType.arrayType());
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        if (Map.class.isAssignableFrom(type)) return; // not ignored, just handled differently and somewhere else, DatabaseRelationshipHandler
        if (type.isArray()) {
            if (!sqlType.supportsArrays()) return; // not ignored, just handled differently and somewhere else, likely in DatabaseRelationshipHandler

            String resolvedType = resolverRegistry.getType(type.getComponentType()) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        String resolvedType = resolverRegistry.getType(type);
        if (resolvedType == null) {
            TypeResolver<?> resolver = createResolver(data);
            resolvedType = resolverRegistry.getType(resolver);
        }

        RepositoryInformation metadata;
        if (resolvedType == null && (metadata = RepositoryMetadata.getMetadata(type)) != null) {
            // not created properly, last resort
            resolvedType = resolverRegistry.getType(metadata.getPrimaryKey().type());
        }

        Objects.requireNonNull(resolvedType, "Unsupported type: " + type);

        appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
    }

    private void appendColumn(StringJoiner joiner, FieldData<?> data, StringBuilder fieldBuilder, String name, boolean unique, boolean primaryKey, StringJoiner primaryKeysJoiner, StringJoiner relationshipsJoiner, String resolvedType) {
        fieldBuilder.append(name).append(' ').append(resolvedType);

        addColumnMetaData(data, fieldBuilder, unique);

        fieldBuilder.trimToSize();
        joiner.add(fieldBuilder.toString());
        if (primaryKey) primaryKeysJoiner.add(name);

        addPotentialManyToOne(data, name, relationshipsJoiner);
    }

    private void addColumnMetaData(FieldData<?> data, StringBuilder fieldBuilder, boolean unique) {
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
    private @Nullable TypeResolver<Object>  parseNewResolver(final @NotNull FieldData<?> data) {
        ResolveWith annotation = data.getAnnotation(ResolveWith.class);
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

    private static void addPotentialManyToOne(@NotNull FieldData<?> data, String name, StringJoiner relationshipsJoiner) {
        if (data.manyToOne() == null) return;
        RepositoryInformation parent = RepositoryMetadata.getMetadata(data.type());
        Objects.requireNonNull(parent, "Parent should not be null");

        String table = parent.getRepositoryName();

        String primaryKeyName = parent.getPrimaryKey().name();
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