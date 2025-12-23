package io.github.flameyossnow.universal.cassandra.query;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CassandraQueryEngine {
    private final RepositoryInformation repositoryInformation;
    private final Map<String, String> queryMap;
    private final CassandraQueryValidator validator;
    private final String insert;
    private final String insertWithDuplicateUpdate;

    private static @NotNull String getSelectKey(SelectQuery query, final boolean first) {
        if (query == null) {
            return first ? "QUERY:SELECT:FIRST" : "QUERY:SELECT";
        }
        return first ? "QUERY:SELECT:FIRST:" + query.hashCode() : ("QUERY:SELECT:" + query.hashCode());
    }

    public CassandraQueryEngine(final RepositoryInformation repositoryInformation) {
        this.repositoryInformation = repositoryInformation;
        this.queryMap = new ConcurrentHashMap<>(5);
        this.validator = new CassandraQueryValidator(repositoryInformation);
        this.insert = parseInsert0();
        this.insertWithDuplicateUpdate = parseInsertWithDuplicateUpdate0();
    }

    public @NotNull String parseSelect(SelectQuery query, boolean first) {
        String tableName = repositoryInformation.getRepositoryName();

        if (query == null) {
            return "SELECT * FROM `" + tableName + "`" + (first ? " LIMIT 1" : "");
        }

        String selectKey = getSelectKey(query, first);
        String existing = queryMap.get(selectKey);
        if (existing != null) {
            return existing;
        }

        ValidationEstimation validationEstimation = this.validator.validateSelectQuery(query);
        if (validationEstimation.isFail()) {
            throw new IllegalArgumentException("Invalid query: " + validationEstimation.reason());
        }

        StringBuilder cql = new StringBuilder("SELECT * FROM `")
                .append(tableName)
                .append('`');

        appendConditions(query, cql);
        appendSortingAndLimit(query, cql, first);

        String generated = cql.toString();
        this.queryMap.put(selectKey, generated);
        return generated;
    }

    private static void appendConditions(@NotNull SelectQuery query, StringBuilder cql) {
        if (!query.filters().isEmpty()) {
            cql.append(" WHERE ");
            cql.append(buildConditions(query.filters()));
        }
    }

    private static void appendSortingAndLimit(@NotNull SelectQuery query, StringBuilder cql, boolean first) {
        if (!query.sortOptions().isEmpty()) {
            cql.append(" ORDER BY ");
            cql.append(buildSortOptions(query.sortOptions()));
        }

        if (query.limit() != -1) {
            cql.append(" LIMIT ").append(query.limit());
        } else if (first) {
            cql.append(" LIMIT 1");
        }
    }

    private static String buildConditions(@NotNull Iterable<SelectOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption filter : filters) {
            // Handle IN clause specially
            if ("IN".equalsIgnoreCase(filter.operator())) {
                Object value = filter.value();
                if (value instanceof List<?> list) {
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

    public String parseInsert() {
        return this.insert;
    }

    public String parseInsertWithDuplicateUpdate() {
        return this.insertWithDuplicateUpdate;
    }

    public @NotNull String parseInsert0() {
        StringBuilder queryBuilder = getStringBuilder();
        return queryBuilder.toString();
    }

    public @NotNull String parseInsertWithDuplicateUpdate0() {
        StringBuilder queryBuilder = getStringBuilder();
        queryBuilder.append(" ON DUPLICATE KEY UPDATE");
        return queryBuilder.toString();
    }

    private @NotNull StringBuilder getStringBuilder() {
        StringJoiner columnJoiner = new StringJoiner(", ");
        StringBuilder queryBuilder = new StringBuilder("INSERT INTO ");
        StringJoiner joiner = new StringJoiner(", ");

        queryBuilder.append(repositoryInformation.getRepositoryName()).append(" (");

        for (FieldData<?> data : repositoryInformation.getFields()) {
            if (Collection.class.isAssignableFrom(data.type()) || Map.class.isAssignableFrom(data.type())) continue;
            if (data.autoIncrement())
                joiner.add("default");
            else
                joiner.add("?");
            columnJoiner.add(data.name());
        }

        queryBuilder.append(columnJoiner).append(") VALUES (").append(joiner).append(')').append(';');
        return queryBuilder;
    }

    public String parseIndex(final @NotNull IndexOptions index) {
        if (index.type() != IndexType.NORMAL) {
            throw new IllegalArgumentException("Cannot create an index of type " + index.type() + " in Cassandra.");
        }
        return String.format("CREATE INDEX `%s` ON `%s` (%s);", index.indexName(), repositoryInformation.getRepositoryName(), index.getJoinedFields());
    }

    private static @NotNull String generateSetClause(@NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) joiner.add(key + " = ?");
        return joiner.toString();
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

    public @NotNull String parseUpdate(UpdateQuery query) {
        String key = "UPDATE:" + query.hashCode();
        return queryMap.computeIfAbsent(key, k -> {
            String tableName = repositoryInformation.getRepositoryName();
            String setClause = generateSetClause(query);

            return query.filters().isEmpty()
                    ? String.format("UPDATE %s SET %s;", tableName, setClause)
                    : String.format("UPDATE %s SET %s WHERE %s;", tableName, setClause, buildConditions(query.filters()));
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
                return String.format("UPDATE %s SET %s WHERE %s;", tableName, setClause, whereClause);
            } else {
                // For single primary key
                return String.format("UPDATE %s SET %s WHERE %s = ?;", tableName, setClause, repositoryInformation.getPrimaryKey().name());
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
}
