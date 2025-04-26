package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class QueryParseEngine {
    private final SQLType sqlType;
    private final RepositoryInformation repositoryInformation;
    private final Map<String, String> queryMap;

    private final String insert;

    public QueryParseEngine(SQLType sqlType, final RepositoryInformation repositoryInformation) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.queryMap = new HashMap<>();
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
        return String.format("CREATE %sINDEX %s ON %s (%s);", type, index.indexName(), repositoryInformation.getRepositoryName(), index.getJoinedFields());
    }

    public @NotNull String parseSelect(SelectQuery query, boolean first) {
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

    private @NotNull String parseQuery(SelectQuery query, boolean first) {
        String tableName = repositoryInformation.getRepositoryName();

        if (query == null) {
            return "SELECT * FROM " + tableName + (first ? " LIMIT 1" : "");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);

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
        return query == null || query.filters().isEmpty()
                ? "DELETE FROM " + repositoryInformation.getRepositoryName()
                : "DELETE FROM " + repositoryInformation.getRepositoryName() + " WHERE " + buildConditions(query.filters());
    }

    public @NotNull String parseDelete(Object value) {
        if (value.getClass() != repositoryInformation.getType())
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.getType());

        return "DELETE FROM " + repositoryInformation.getRepositoryName() + " WHERE " + repositoryInformation.getPrimaryKey().name() + " = ?";
    }

    public @NotNull String parseInsert() {
        Logging.info("Parsed query for inserting: " + insert);
        return insert;
    }

    public @NotNull String parseInsert0() {
        StringJoiner columnJoiner = new StringJoiner(", ");

        StringBuilder queryBuilder = new StringBuilder("INSERT INTO ");
        queryBuilder.append(repositoryInformation.getRepositoryName()).append(" (");

        StringJoiner joiner = new StringJoiner(", ");
        for (FieldData<?> data : repositoryInformation.getFields()) {
            if (List.class.isAssignableFrom(data.type())) continue;
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
        String tableName = repositoryInformation.getRepositoryName();
        String setClause = generateSetClause(query);

        return query.conditions().isEmpty()
                ? String.format("UPDATE %s SET %s;", tableName, setClause)
                : String.format("UPDATE %s SET %s WHERE %s;", tableName, setClause, buildConditions(query.conditions()));
    }

    public @NotNull String parseUpdateFromEntity() {
        String tableName = repositoryInformation.getRepositoryName();
        String setClause = generateSetClauseFromEntity();

        return String.format("UPDATE %s SET %s WHERE %s = ?;", tableName, setClause, repositoryInformation.getPrimaryKey().name());
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
        StringJoiner joiner = new StringJoiner(", ", "CREATE TABLE " + (ifNotExists ? "IF NOT EXISTS " : "") + repositoryInformation.getRepositoryName() + " (", ");");

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
            StringBuilder fieldBuilder = new StringBuilder();
            final Class<?> type = data.type();
            final boolean primaryKey = data.primary();
            final boolean unique = data.unique();
            final String name = data.name();

            Logging.deepInfo("Processing field: " + name);
            Logging.deepInfo("Field type: " + type);

            if (Collection.class.isAssignableFrom(type)) continue;
            String resolvedType = ValueTypeResolverRegistry.INSTANCE.getType(type);
            fieldBuilder.append(name).append(' ').append(resolvedType);

            addColumnMetaData(data, fieldBuilder, unique);

            joiner.add(fieldBuilder.toString());
            if (primaryKey) primaryKeysJoiner.add(name);

            addPotentialManyToOne(data, name, relationshipsJoiner);
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

    private void addColumnMetaData(FieldData<?> data, StringBuilder fieldBuilder, boolean unique) {
        if (data.nonNull()) fieldBuilder.append(" NOT NULL");
        if (data.autoIncrement()) fieldBuilder.append(" ").append(getAutoIncrementKeyword());
        if (data.condition() != null) fieldBuilder.append(" CHECK (").append(data.condition().value()).append(")");
        if (unique) fieldBuilder.append(" UNIQUE");
    }

    private static void addPotentialManyToOne(FieldData<?> data, String name, StringJoiner relationshipsJoiner) {
        if (data.manyToOne() != null) {
            RepositoryInformation parent = RepositoryMetadata.getMetadata(data.type());
            String table = parent.getRepositoryName();
            StringBuilder fkBuilder = new StringBuilder();
            fkBuilder.append("FOREIGN KEY (").append(name).append(") REFERENCES ").append(table).append("(").append(parent.getPrimaryKey().name()).append(")");
            if (data.onDelete() != null) fkBuilder.append(" ON DELETE ").append(data.onDelete().value().name());
            if (data.onUpdate() != null) fkBuilder.append(" ON UPDATE ").append(data.onUpdate().value().name());
            relationshipsJoiner.add(fkBuilder);
        }
    }

    private @NotNull String getAutoIncrementKeyword() {
        return sqlType == SQLType.SQLITE ? "AUTOINCREMENT" : "AUTO_INCREMENT";
    }

    private static @NotNull String generateSetClause(@NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) joiner.add(key + " = ?");
        return joiner.toString();
    }

    private static String buildConditions(@NotNull Iterable<SelectOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption filter : filters) {
            joiner.add(filter.option() + " " + filter.operator() + " ?");
        }
        return joiner.toString();
    }

    private static String buildSortOptions(@NotNull Iterable<SortOption> sortOptions) {
        StringJoiner joiner = new StringJoiner(", ");
        for (SortOption sortOption : sortOptions) joiner.add(sortOption.field() + " " + (sortOption.order() == SortOrder.ASCENDING ? "ASC" : "DESC"));
        return joiner.toString();
    }

    public enum SQLType {
        MYSQL, SQLITE
    }
}