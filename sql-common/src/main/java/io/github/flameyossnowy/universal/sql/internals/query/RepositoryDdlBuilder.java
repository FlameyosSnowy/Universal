package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.meta.ConstraintModel;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.SqlEncoding;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public final class RepositoryDdlBuilder<T, ID> {
    private final QueryParseEngine.SQLType sqlType;
    private final RepositoryModel<T, ID> repositoryInformation;
    private final TypeResolverRegistry resolverRegistry;
    private final SQLConnectionProvider connectionProvider;

    public RepositoryDdlBuilder(
        QueryParseEngine.SQLType sqlType,
        RepositoryModel<T, ID> repositoryInformation,
        TypeResolverRegistry resolverRegistry,
        SQLConnectionProvider connectionProvider
    ) {
        this.sqlType = sqlType;
        this.repositoryInformation = repositoryInformation;
        this.resolverRegistry = resolverRegistry;
        this.connectionProvider = connectionProvider;
    }

    @SuppressWarnings({ "RedundantOperationOnEmptyContainer", "ConstantValue" })
    public @NotNull String parseRepository(boolean ifNotExists) {
        Logging.deepInfo(() -> "Starting repository parse: " + repositoryInformation.tableName());
        Logging.deepInfo(() -> "IF NOT EXISTS = " + ifNotExists);

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        Set<FieldModel<T>> childTableQueue = new HashSet<>(4);

        String tableName = repositoryInformation.tableName();
        String ddlPrefix = "CREATE TABLE " + (ifNotExists ? "IF NOT EXISTS " : "")
            + sqlType.quoteChar() + tableName + sqlType.quoteChar();

        Logging.deepInfo(() -> "DDL prefix: " + ddlPrefix);

        StringJoiner joiner = new StringJoiner(", ", ddlPrefix + " (", ");");

        generateColumns(joiner, childTableQueue);

        String classConstraints = processClassLevelConstraints();
        if (!classConstraints.isEmpty()) {
            Logging.deepInfo(() -> "Adding class-level constraints: " + classConstraints);
            joiner.add(classConstraints);
        }

        String finalQuery = joiner.toString();
        Logging.deepInfo(() -> "Final CREATE TABLE query:\n" + finalQuery);

        String query = createTable(finalQuery, "Failed to create main repository table: ", tableName);

        if (!childTableQueue.isEmpty()) {
            Logging.deepInfo(() ->  "Creating " + childTableQueue.size() + " child tables");
        }

        for (FieldModel<T> data : childTableQueue) {
            Logging.deepInfo(() -> "Creating child table for field: " + data.name());
            createChildTable(data);
        }

        return query;
    }

    private String createTable(String query, String errorMessage, String repositoryName) {
        System.out.println(query);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connectionProvider.prepareStatement(query, connection)) {
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(errorMessage + repositoryName, e);
        }
        return query;
    }

    private String processClassLevelConstraints() {
        List<ConstraintModel> constraints = repositoryInformation.constraints();
        if (constraints.isEmpty()) {
            Logging.deepInfo(() -> "No class-level constraints found");
            return "";
        }

        Logging.deepInfo(() -> "Processing " + constraints.size() + " class-level constraints");

        StringJoiner joiner = new StringJoiner(", ");
        for (ConstraintModel constraint : constraints) {
            Logging.deepInfo(() -> "Processing constraint: " + constraint.name());

            StringJoiner checkConditionsJoiner = new StringJoiner(" AND ");
            StringJoiner uniqueFieldsJoiner = new StringJoiner(", ");

            for (String fieldName : constraint.fields()) {
                FieldModel<T> fieldData = repositoryInformation.fieldByName(fieldName);
                if (fieldData == null) {
                    Logging.deepInfo(() -> "Constraint field not found: " + fieldName);
                    continue;
                }

                if (fieldData.condition() != null) {
                    checkConditionsJoiner.add(fieldData.condition().value());
                }
                if (fieldData.hasUniqueAnnotation()) {
                    uniqueFieldsJoiner.add(fieldName);
                }
            }

            if (checkConditionsJoiner.length() > 0) {
                String check = "CONSTRAINT " + constraint.name() + " CHECK (" + checkConditionsJoiner + ")";
                Logging.deepInfo(() -> "Generated CHECK constraint: " + check);
                joiner.add(check);
            }

            if (uniqueFieldsJoiner.length() > 0) {
                String unique = "CONSTRAINT " + constraint.name() + " UNIQUE (" + uniqueFieldsJoiner + ")";
                Logging.deepInfo(() -> "Generated UNIQUE constraint: " + unique);
                joiner.add(unique);
            }
        }

        return joiner.toString();
    }

    @Contract(pure = true)
    private void generateColumns(final StringJoiner joiner, Set<FieldModel<T>> childTableQueue) {
        Logging.deepInfo(() -> "Generating columns for repository: " + repositoryInformation.tableName());

        StringJoiner primaryKeysJoiner = new StringJoiner(", ");
        StringJoiner relationshipsJoiner = new StringJoiner(", ");

        for (FieldModel<T> data : repositoryInformation.fields()) {
            Logging.deepInfo(() -> "----");
            Logging.deepInfo(() -> "Processing field: " + data.name());

            generateColumn(
                joiner,
                data,
                data.type(),
                new StringBuilder(32),
                data.name(),
                data.hasUniqueAnnotation(),
                data.id(),
                primaryKeysJoiner,
                relationshipsJoiner,
                childTableQueue
            );
        }

        if (primaryKeysJoiner.length() > 0) {
            String pk = "PRIMARY KEY (" + primaryKeysJoiner + ")";
            Logging.deepInfo(() -> "Primary key clause: " + pk);
            joiner.add(pk);
        }

        if (relationshipsJoiner.length() > 0) {
            Logging.deepInfo(() -> "Relationship clauses: " + relationshipsJoiner);
            joiner.add(relationshipsJoiner.toString());
        }
    }

    private void generateColumn(
        StringJoiner joiner,
        FieldModel<T> data,
        Class<?> type,
        StringBuilder fieldBuilder,
        String name,
        boolean unique,
        boolean primaryKey,
        StringJoiner primaryKeysJoiner,
        StringJoiner relationshipsJoiner,
        Set<FieldModel<T>> childTableQueue
    ) {
        if (data.relationshipKind() == RelationshipKind.ONE_TO_MANY) {
            return;
        }

        if (Collection.class.isAssignableFrom(type)) {
            if (!sqlType.supportsArrays()) {
                childTableQueue.add(data);
                return;
            }

            Class<?> genericType = data.elementType();
            String resolvedType = resolverRegistry.getType(genericType, data.hasBinaryAnnotation() ? SqlEncoding.BINARY : SqlEncoding.VISUAL) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        if (Map.class.isAssignableFrom(type)) {
            childTableQueue.add(data);
            return;
        }

        if (type.isArray()) {
            if (!sqlType.supportsArrays()) {
                childTableQueue.add(data);
                return;
            }
            String resolvedType = resolverRegistry.getType(type.getComponentType(), data.hasBinaryAnnotation() ? SqlEncoding.BINARY : SqlEncoding.VISUAL) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        String resolvedType = resolverRegistry.getType(type, data.hasBinaryAnnotation() ? SqlEncoding.BINARY : SqlEncoding.VISUAL);

        RepositoryModel<T, ID> metadata;
        if (resolvedType == null && (metadata = (RepositoryModel<T, ID>) GeneratedMetadata.getByEntityClass(type)) != null) {
            FieldModel<T> metadataPrimaryKey = metadata.getPrimaryKey();
            Objects.requireNonNull(metadataPrimaryKey, "Primary key must not be null");
            resolvedType = resolverRegistry.getType(metadataPrimaryKey.type(), data.hasBinaryAnnotation() ? SqlEncoding.BINARY : SqlEncoding.VISUAL);
        }

        Objects.requireNonNull(resolvedType, "Unsupported type: " + type);

        appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
    }

    private void appendColumn(
        @NotNull StringJoiner joiner,
        FieldModel<T> data,
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
        Logging.deepInfo(() -> "Generated column SQL: " + columnSql);

        joiner.add(columnSql);

        if (primaryKey) {
            Logging.deepInfo(() -> "Marked as PRIMARY KEY: " + name);
            primaryKeysJoiner.add(name);
        }

        addPotentialManyToOne(data, name, relationshipsJoiner);
    }

    private void addColumnMetaData(@NotNull FieldModel<T> data, StringBuilder fieldBuilder, boolean unique) {
        if (!data.nullable()) fieldBuilder.append(" NOT NULL");
        if (data.autoIncrement()) fieldBuilder.append(' ').append(sqlType.autoIncrementKeyword());
        if (data.condition() != null) fieldBuilder.append(" CHECK (").append(data.condition().value()).append(')');
        if (unique) fieldBuilder.append(" UNIQUE");
    }

    private void createChildTable(FieldModel<T> data) {
        String childTableName = repositoryInformation.tableName() + "_" + data.name() + "s";
        Class<?> elementType = (data.elementType() != null) ? data.elementType() : Object.class;

        String elementSqlType = resolverRegistry.getType(elementType);
        FieldModel<T> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Should be nonnull.");
        }

        String idSqlType = resolverRegistry.getType(primaryKey.type());

        StringBuilder sb = new StringBuilder(128);
        sb.append("CREATE TABLE IF NOT EXISTS ").append(sqlType.quoteChar()).append(childTableName).append(sqlType.quoteChar())
            .append(" (\n")
            .append("  ").append(sqlType.quoteChar()).append("id").append(sqlType.quoteChar()).append(' ').append(idSqlType).append(" NOT NULL,\n");

        if (Collection.class.isAssignableFrom(data.type())) {
            sb.append("  ").append(sqlType.quoteChar()).append("value").append(sqlType.quoteChar()).append(' ').append(elementSqlType).append(" NOT NULL,\n");
        } else if (Map.class.isAssignableFrom(data.type())) {
            sb.append("  ").append(sqlType.quoteChar()).append("map_key").append(sqlType.quoteChar()).append(' ').append(resolverRegistry.getType(data.mapKeyType())).append(" NOT NULL,\n")
                .append("  ").append(sqlType.quoteChar()).append("map_value").append(sqlType.quoteChar()).append(' ').append(elementSqlType).append(" NOT NULL,\n");
        }

        sb.append("  FOREIGN KEY (").append(sqlType.quoteChar()).append("id").append(sqlType.quoteChar()).append(") REFERENCES ")
            .append(sqlType.quoteChar()).append(repositoryInformation.tableName()).append(sqlType.quoteChar()).append('(')
            .append(sqlType.quoteChar()).append("id").append(sqlType.quoteChar()).append(") ON DELETE CASCADE ON UPDATE CASCADE\n")
            .append(");");

        createTable(sb.toString(), "Failed to create child table: ", childTableName);
    }

    private void addPotentialManyToOne(@NotNull FieldModel<T> data, String name, StringJoiner relationshipsJoiner) {
        if (data.relationshipKind() != RelationshipKind.MANY_TO_ONE && data.relationshipKind() != RelationshipKind.ONE_TO_ONE) return;
        RepositoryModel<T, ID> parent = (RepositoryModel<T, ID>) GeneratedMetadata.getByEntityClass(data.type());
        Objects.requireNonNull(parent, "Parent should not be null");

        String table = parent.tableName();

        String primaryKeyName = parent.getPrimaryKey().name();
        String onDelete = data.onDelete() != null ? data.onDelete().value().name() : "";
        String onUpdate = data.onUpdate() != null ? data.onUpdate().value().name() : "";

        StringBuilder fkBuilder = new StringBuilder(38 + name.length() + table.length() + primaryKeyName.length() + onDelete.length() + onUpdate.length());
        fkBuilder.append("FOREIGN KEY (").append(name).append(") REFERENCES ").append(sqlType.quoteChar()).append(table).append(sqlType.quoteChar()).append('(').append(primaryKeyName).append(')');
        if (data.onDelete() != null) fkBuilder.append(" ON DELETE ").append(onDelete);
        if (data.onUpdate() != null) fkBuilder.append(" ON UPDATE ").append(onUpdate);
        relationshipsJoiner.add(fkBuilder);
    }
}
