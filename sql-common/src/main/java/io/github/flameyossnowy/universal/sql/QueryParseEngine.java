package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class QueryParseEngine {
    private final SQLType sqlType;
    private final RepositoryInformation repositoryInformation;
    private final ValueTypeResolverRegistry registry;
    private final Map<String, String> queryMap;

    public QueryParseEngine(SQLType sqlType, final RepositoryInformation repositoryInformation, final ValueTypeResolverRegistry registry, EnumSet<Optimizations> optimizations) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.registry = registry;
        this.queryMap = optimizations != null && optimizations.contains(Optimizations.CACHE_PARSED_QUERIES) ? new HashMap<>() : null;
    }

    private static String getInsertKey() {
        return "QUERY:INSERT";
    }

    private static String getSelectKey(SelectQuery query, final boolean first) {
        return first ? "QUERY:SELECT:FIRST:" + query.hashCode() : ("QUERY:SELECT:" + query.hashCode());
    }

    public @NotNull String parseInsert(@NotNull Collection<FieldData<?>> fields) {
        if (this.queryMap != null) {
            String queryString = this.queryMap.get(getInsertKey());
            if (queryString != null) return queryString;
        }
        StringJoiner columnJoiner = new StringJoiner(", ");
        for (FieldData<?> data : fields) {
            if (data.autoIncrement()) continue;
            columnJoiner.add(data.name());
        }

        String query = String.format("INSERT INTO %s (%s) VALUES (%s);",
                repositoryInformation.repository(), columnJoiner, generatePlaceholders(fields.size() - 1));

        if (this.queryMap != null) this.queryMap.put(getInsertKey(), query);
        return query;
    }

    public @NotNull String parseUpdate(UpdateQuery query) {
        String tableName = repositoryInformation.repository();
        String setClause = generateSetClause(query);

        return query.conditions().isEmpty()
                ? String.format("UPDATE %s SET %s;", tableName, setClause)
                : String.format("UPDATE %s SET %s WHERE %s;", tableName, setClause, generateConditionClause(query));
    }

    public @NotNull String parseRepository(boolean ifNotExists) {
        StringJoiner joiner = new StringJoiner(", ", "CREATE TABLE " + (ifNotExists ? "IF NOT EXISTS " : "") + repositoryInformation.repository() + " (", ");");

        Constraint[] constraints = repositoryInformation.constraints();
        Set<String> constrainedFields = constraints.length > 0 ? new HashSet<>(constraints.length) : Collections.emptySet();
        if (constraints.length > 0) addConstraints(constrainedFields);

        generateColumns(joiner, constrainedFields);

        String classConstraints = processClassLevelConstraints();
        if (!classConstraints.isEmpty()) {
            joiner.add(classConstraints);
        }

        return joiner.toString();
    }

    private void addConstraints(final Set<String> constrainedFields) {
        for (Constraint c : repositoryInformation.constraints()) {
            Collections.addAll(constrainedFields, c.fields());
        }
    }

    private String processClassLevelConstraints() {
        if (repositoryInformation.constraints().length == 0) return "";

        StringJoiner joiner = new StringJoiner(", ");
        for (Constraint constraint : repositoryInformation.constraints()) {
            String[] fields = constraint.fields();
            if (fields.length == 0) continue;

            StringJoiner checkConditionsJoiner = new StringJoiner(" AND ");
            StringJoiner uniqueFieldsJoiner = new StringJoiner(", ");

            for (String fieldName : fields) {
                FieldData<?> fieldData = repositoryInformation.fieldData().get(fieldName);
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
    private void generateColumns(final StringJoiner joiner, final Set<String> constrainedFields) {
        StringJoiner primaryKeysJoiner = new StringJoiner(", ");
        StringJoiner uniqueKeysJoiner = new StringJoiner(", ");

        for (FieldData<?> data : repositoryInformation.fields()) {
            StringBuilder fieldBuilder = new StringBuilder();
            final boolean primaryKey = data.primary();
            final boolean unique = data.unique();
            final String name = data.name();

            String resolvedType = registry.getType(data.type());
            fieldBuilder.append(name).append(' ').append(resolvedType);

            if (data.nonNull()) fieldBuilder.append(" NOT NULL");
            if (data.autoIncrement()) fieldBuilder.append(getAutoIncrementKeyword());
            if (data.condition() != null) fieldBuilder.append(" CHECK (").append(data.condition().value()).append(")");
            if (unique) fieldBuilder.append(" UNIQUE");
            if (data.foreign() != null && data.references() != null)
                appendForeignKeyConstraints(data, fieldBuilder);


            joiner.add(fieldBuilder.toString());
            if (primaryKey) {
                primaryKeysJoiner.add(name);
            }
            if (unique && constrainedFields.contains(name)) {
                uniqueKeysJoiner.add(name);
            }
        }

        String pkClause = primaryKeysJoiner.toString();
        if (!pkClause.isEmpty()) {
            joiner.add("PRIMARY KEY (" + pkClause + ")");
        }

        String uniqueClause = uniqueKeysJoiner.toString();
        if (!uniqueClause.isEmpty()) {
            joiner.add("UNIQUE (" + uniqueClause + ")");
        }
    }

    private static void appendForeignKeyConstraints(@NotNull FieldData<?> data,
                                                    StringBuilder fieldBuilder) {
        References references = data.references();
        if (references.field().isEmpty()) {
            throw new RuntimeException("FOREIGN KEY constraint requires referenced table and field.");
        }
        fieldBuilder.append(" REFERENCES ").append(references.table())
                .append(" (").append(references.field()).append(")");

        if (data.onDelete() != null) {
            fieldBuilder.append(" ON DELETE ").append(data.onDelete().action());
        }
        if (data.onUpdate() != null) {
            fieldBuilder.append(" ON UPDATE ").append(data.onUpdate().action());
        }
    }

    private @NotNull String getAutoIncrementKeyword() {
        return sqlType == SQLType.SQLITE ? "AUTOINCREMENT" : "AUTO_INCREMENT";
    }

    private static @NotNull String generateConditionClause(@NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption selectOption : query.conditions()) {
            joiner.add(selectOption.option() + " " + selectOption.operator() + " ?");
        }
        return joiner.toString();
    }

    private static @NotNull String generateSetClause(@NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) {
            joiner.add(key + " = ?");
        }
        return joiner.toString();
    }

    private static @NotNull String generatePlaceholders(final int size) {
        return "?".repeat(Math.max(0, size)).trim();
    }

    public String parseIndex(final IndexOptions index) {
        String type = index.type() == IndexType.NORMAL ? "" : index.type().name() + " ";
        return String.format(
                "CREATE %sINDEX %s ON %s (%s);",
                type, index.indexName(), repositoryInformation.repository(), index.getJoinedFields()
        );
    }

    public @NotNull String parseSelect(SelectQuery query, boolean first) {
        String key = null;

        String tableName = repositoryInformation.repository();
        if (query == null) return "SELECT * FROM " + tableName;

        if (queryMap != null) {
            key = getSelectKey(query, first);
            String queryString = queryMap.get(key);
            if (queryString != null) return queryString;
        }

        String columns = query.columns().isEmpty() ? "*" : String.join(", ", query.columns());

        String filters = query.filters().isEmpty() ? "" : "WHERE " + buildConditions(query.filters());
        String sortOptions = query.sortOptions().isEmpty() ? "" : "ORDER BY " + buildSortOptions(query.sortOptions());

        String queryString = String.format("SELECT %s FROM %s %s %s %s;",
                columns, tableName, filters, sortOptions,
                query.limit() == -1 ? "" : "LIMIT " + query.limit());

        if (queryMap != null) queryMap.put(key, queryString);
        System.out.println(queryString);
        return queryString;
    }

    public @NotNull String parseSelect(SelectQuery query) {
        return parseSelect(query, false);
    }

    public @NotNull String parseDelete(DeleteQuery query) {
        return query.filters().isEmpty()
                ? "DELETE FROM " + repositoryInformation.repository()
                : "DELETE FROM " + repositoryInformation.repository() + " WHERE " + buildConditions(query.filters());
    }

    public @NotNull String parseDelete(DeleteQuery query, Object value) {
        if (value == null) return parseDelete(query);
        if (value.getClass() != repositoryInformation.type())
            throw new IllegalArgumentException("Value must be of type " + repositoryInformation.type());

        StringJoiner joiner = new StringJoiner(" AND ");
        if (query != null) for (SelectOption filter : query.filters()) joiner.add(filter.option() + " " + filter.operator() + " ?");

        for (FieldData<?> data : repositoryInformation.fields()) {
            joiner.add(data.name() + " = ?");
        }
        return "DELETE FROM " + repositoryInformation.repository() + " WHERE " + joiner;
    }

    private static String buildConditions(@NotNull Iterable<SelectOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption filter : filters) joiner.add(filter.option() + " " + filter.operator() + " ?");
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