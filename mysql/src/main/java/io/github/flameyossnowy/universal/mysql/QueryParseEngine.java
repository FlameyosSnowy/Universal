package io.github.flameyossnowy.universal.mysql;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.Index;
import io.github.flameyossnowy.universal.api.annotations.References;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.repository.RepositoryMetadata;
import io.github.flameyossnowy.universal.mysql.annotations.MySQLResolver;
import io.github.flameyossnowy.universal.mysql.resolvers.MySQLValueTypeResolver;
import io.github.flameyossnowy.universal.mysql.resolvers.ValueTypeResolverRegistry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("unchecked")
public class QueryParseEngine {
    private Map<SelectQuery, String> queryMap;
    
    public QueryParseEngine(EnumSet<Optimizations> optimizations) {
        if (optimizations == null) return;
        if (optimizations.contains(Optimizations.CACHE_PARSED_QUERIES)) {
            this.queryMap = new HashMap<>();
        }
    }

    public @NotNull String parseSelect(SelectQuery query, Class<?> repository) {
        if (queryMap != null) {
            String queryString = queryMap.get(query);
            if (queryString != null) return queryString;
        }

        String tableName = RepositoryMetadata.getMetadata(repository).repository();
        if (query == null) return "SELECT * FROM " + tableName;

        String columns = query.columns().isEmpty() ? "*" : String.join(", ", query.columns());
        String joins = query.joins().isEmpty() ? "" : buildJoins(query.joins());
        String filters = query.filters().isEmpty() ? "" : "WHERE " + buildConditions(query.filters());
        String sortOptions = query.sortOptions().isEmpty() ? "" : "ORDER BY " + buildSortOptions(query.sortOptions());

        String queryString = String.format("SELECT %s FROM %s %s %s %s %s;", columns, tableName, joins, filters, sortOptions, query.limit() == -1 ? "" : "LIMIT " + query.limit());

        if (queryMap != null) queryMap.put(query, queryString);
        return queryString;
    }

    public @NotNull String parseInsert(RepositoryMetadata.RepositoryInformation information, @NotNull Collection<RepositoryMetadata.FieldData<?>> fields) {
        StringJoiner columnJoiner = new StringJoiner(", ");
        for (RepositoryMetadata.FieldData<?> data : fields) {
            if (data.autoIncrement()) continue;
            columnJoiner.add(data.name());
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s);", information.repository(), columnJoiner, generatePlaceholders(fields.size()));
    }

    public @NotNull String parseUpdate(UpdateQuery query, Class<?> repository) {
        String tableName = RepositoryMetadata.getMetadata(repository).repository();
        String setClause = generateSetClause(query);

        String queryString;
        if (query.conditions() == null || query.conditions().isEmpty()) {
            queryString = String.format("UPDATE %s SET %s", tableName, setClause);
        } else {
            String whereClause = generateConditionClause(query);
            queryString = String.format("UPDATE %s SET %s WHERE %s;", tableName, setClause, whereClause);
        }

        return queryString;
    }

    public @NotNull String parseRepository(@NotNull RepositoryMetadata.RepositoryInformation metadata, MySQLRepositoryAdapter<?> repository) {
        StringJoiner joiner = new StringJoiner(", ", "CREATE TABLE IF NOT EXISTS " + metadata.repository() + " (", ");");

        Set<String> constrainedFields = new HashSet<>();
        for (Constraint c : metadata.constraints()) {
            Collections.addAll(constrainedFields, c.fields());
        }

        generateColumns(metadata, joiner, repository, constrainedFields);

        String classConstraints = processClassLevelConstraints(metadata);
        if (!classConstraints.isEmpty()) {
            joiner.add(classConstraints);
        }

        return joiner.toString();
    }

    @Contract(pure = true)
    private static void generateColumns(@NotNull RepositoryMetadata.RepositoryInformation metadata,
                                        StringJoiner joiner,
                                        MySQLRepositoryAdapter<?> repository,
                                        final Set<String> constrainedFields) {
        StringJoiner primaryKeysJoiner = new StringJoiner(", ");
        StringJoiner uniqueKeysJoiner = new StringJoiner(", ");

        for (RepositoryMetadata.FieldData<?> data : metadata.fields()) {
            Result result = generateColumn(data, repository);
            joiner.add(result.column);
            if (result.hasPrimaryKey) {
                primaryKeysJoiner.add(result.name);
            }
            if (result.unique && constrainedFields.contains(result.name)) {
                uniqueKeysJoiner.add(result.name);
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

    private static String processClassLevelConstraints(@NotNull RepositoryMetadata.RepositoryInformation metadata) {
        if (metadata.constraints().length == 0) return "";

        StringJoiner joiner = new StringJoiner(", ");
        for (Constraint constraint : metadata.constraints()) {
            String[] fields = constraint.fields();
            if (fields.length == 0) continue;

            StringJoiner checkConditionsJoiner = new StringJoiner(" AND ");
            StringJoiner uniqueFieldsJoiner = new StringJoiner(", ");

            for (String fieldName : fields) {
                RepositoryMetadata.FieldData<?> fieldData = metadata.fieldData().get(fieldName);
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

    @NotNull List<String> generateIndexes(@NotNull RepositoryMetadata.RepositoryInformation metadata) {
        List<String> indexes = new ArrayList<>();

        for (Index index : metadata.indexes()) {
            if (index.fields().length == 0) continue;

            StringJoiner fieldsJoiner = new StringJoiner(", ");
            for (String field : index.fields()) {
                fieldsJoiner.add(field);
            }

            String indexType = index.unique() ? "UNIQUE INDEX" : "INDEX";
            String indexStatement = "CREATE " + indexType + " IF NOT EXISTS " +
                    index.name() + " ON " + metadata.repository() +
                    " (" + fieldsJoiner + ");";

            indexes.add(indexStatement);
        }

        return indexes;
    }

    private static @NotNull Result generateColumn(@NotNull RepositoryMetadata.FieldData<?> data,
                                                                   @NotNull MySQLRepositoryAdapter<?> repository) {
        StringBuilder fieldBuilder = new StringBuilder();
        final boolean primaryKey = data.primary();
        final boolean unique = data.unique();
        final String name = data.name();

        String resolvedType = resolveType(data, repository);
        fieldBuilder.append(name).append(' ').append(resolvedType);

        if (data.nonNull()) {
            fieldBuilder.append(" NOT NULL");
        }
        if (data.autoIncrement()) {
            fieldBuilder.append(" AUTO_INCREMENT");
        }
        if (data.condition() != null) {
            fieldBuilder.append(" CHECK (").append(data.condition().value()).append(")");
        }
        if (unique) {
            fieldBuilder.append(" UNIQUE");
        }

        if (data.foreign() != null && data.references() != null) {
            appendForeignKeyConstraints(data, fieldBuilder);
        }
        return new Result(primaryKey, unique, name, fieldBuilder.toString());
    }

    private static <T> String resolveType(@NotNull RepositoryMetadata.FieldData<T> data,
                                          @NotNull MySQLRepositoryAdapter<?> repository) {
        var registry = repository.getValueTypeResolverRegistry();
        MySQLValueTypeResolver<T> resolver = (MySQLValueTypeResolver<T>) registry.getResolver(data.type());

        if (resolver == null) {
            resolver = createResolver(data, repository, registry);
        }

        if (resolver == null) {
            throw new IllegalArgumentException("Unknown type: " + data.type() + " and no resolver provided.");
        }

        return registry.getType(resolver);
    }

    // suppress raw use of parameterized class
    @SuppressWarnings("rawtypes")
    private static <T> @Nullable MySQLValueTypeResolver<T> createResolver(final RepositoryMetadata.@NotNull FieldData<T> data, final @NotNull MySQLRepositoryAdapter<?> repository, final ValueTypeResolverRegistry registry) {
        MySQLValueTypeResolver<T> resolver;
        if (Enum.class.isAssignableFrom(data.type())) {
            Class<? extends Enum> enumClass = (Class<? extends Enum<?>>) data.type();

            registry.registerEnum(enumClass);
            resolver = (MySQLValueTypeResolver<T>) registry.getResolver(enumClass);
        } else {
            resolver = parseResolver(data, repository);
        }
        return resolver;
    }

    private static <T> @Nullable MySQLValueTypeResolver<T> parseResolver(final RepositoryMetadata.@NotNull FieldData<T> data,
                                                                          final @NotNull MySQLRepositoryAdapter<?> repository) {
        MySQLResolver annotation = data.rawField().getAnnotation(MySQLResolver.class);
        if (annotation == null) {
            return null;
        }
        MySQLValueTypeResolver<Object> newResolver =
                (MySQLValueTypeResolver<Object>) ReflectiveMetaData.newInstance(annotation.value());
        repository.getValueTypeResolverRegistry().register((Class<Object>) data.type(), newResolver);
        return (MySQLValueTypeResolver<T>) newResolver;
    }

    private static void appendForeignKeyConstraints(@NotNull RepositoryMetadata.FieldData<?> data,
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

    private record Result(boolean hasPrimaryKey, boolean unique, String name, String column) {}

    private static @NotNull String generateConditionClause(final @NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (SelectOption selectOption : query.conditions()) joiner.add(selectOption.option() + " " + selectOption.operator() + " ?");
        return joiner.toString();
    }

    private static @NotNull String generateSetClause(final @NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.updates().keySet()) joiner.add(key + " = ?");
        return joiner.toString();
    }

    private static String generatePlaceholders(final int size) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < size; i++) joiner.add("?");
        return joiner.toString();
    }

    private static String buildJoins(@NotNull Iterable<JoinOption> joins) {
        StringJoiner joiner = new StringJoiner(" ");
        for (JoinOption join : joins) {
            joiner.add(join.joinType() + " JOIN " + join.targetTable() + " ON " + join.onCondition());
        }
        return joiner.toString();
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
}
