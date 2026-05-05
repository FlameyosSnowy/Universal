package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.annotations.Validate;
import io.github.flameyossnowy.universal.api.meta.ConstraintModel;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.meta.ValidationModel;
import io.github.flameyossnowy.universal.api.resolver.SqlEncoding;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
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
    // Optimization: pre-compute whether any field has validation to skip CHECK constraint generation
    private final boolean hasAnyValidation;

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
        // Optimization: compute once during construction
        this.hasAnyValidation = computeHasAnyValidation(repositoryInformation);
    }

    /**
     * Computes whether any field in the repository has validation rules.
     */
    private static <T, ID> boolean computeHasAnyValidation(RepositoryModel<T, ID> repositoryInformation) {
        for (FieldModel<T> field : repositoryInformation.fields()) {
            ValidationModel validation = field.validation();
            if (validation != null && validation.hasValidation()) {
                return true;
            }
        }
        return false;
    }

    public @NotNull String parseRepository(boolean ifNotExists) {
        Logging.deepInfo(() -> "Starting repository parse: " + repositoryInformation.tableName());
        Logging.deepInfo(() -> "IF NOT EXISTS = " + ifNotExists);

        String tableName = repositoryInformation.tableName();
        String ddlPrefix = "CREATE TABLE " + (ifNotExists ? "IF NOT EXISTS " : "")
            + sqlType.quoteChar() + tableName + sqlType.quoteChar();

        Logging.deepInfo(() -> "DDL prefix: " + ddlPrefix);

        StringJoiner joiner = new StringJoiner(", ", ddlPrefix + " (", ");");

        generateColumns(joiner);

        String classConstraints = processClassLevelConstraints();
        if (!classConstraints.isEmpty()) {
            Logging.deepInfo(() -> "Adding class-level constraints: " + classConstraints);
            joiner.add(classConstraints);
        }

        String finalQuery = joiner.toString();
        Logging.deepInfo(() -> "Final CREATE TABLE query:\n" + finalQuery);

        return createTable(finalQuery, "Failed to create main repository table: ", tableName);
    }

    @Contract("_, _, _ -> param1")
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

    private void generateColumns(StringJoiner joiner) {
        Logging.deepInfo(() -> "Generating columns for repository: " + repositoryInformation.tableName());

        StringJoiner primaryKeysJoiner = new StringJoiner(", ");
        StringJoiner relationshipsJoiner = new StringJoiner(", ");

        StringBuilder fieldBuilder = new StringBuilder(128);
        for (FieldModel<T> data : repositoryInformation.fields()) {
            Logging.deepInfo(() -> "----");
            Logging.deepInfo(() -> "Processing field: " + data.name());

            Class<?> type = data.type();
            String name = data.columnName();
            boolean unique = data.hasUniqueAnnotation();
            boolean primaryKey = data.id();

            generateColumn(joiner, data, type, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner);
            fieldBuilder.setLength(0);

            // Add companion version column for @JsonVersioned JSON fields (unless explicitly declared).
            if (data.isJson() && data.jsonVersioned()) {
                String versionColumn = data.columnName() + "_version";
                if (!hasPhysicalColumn(versionColumn, repositoryInformation)) {
                    joiner.add(versionColumn + " INT NOT NULL DEFAULT 1");
                }
            }
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
        StringJoiner relationshipsJoiner
    ) {
        if (data.isJson()) {
            String resolvedType = resolveJsonColumnType(data);
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        if (data.relationshipKind() == RelationshipKind.ONE_TO_MANY) {
            return;
        }

        SqlEncoding encoding = data.hasBinaryAnnotation() ? SqlEncoding.BINARY : SqlEncoding.VISUAL;

        if (Collection.class.isAssignableFrom(type)) {
            if (!sqlType.supportsArrays()) {
                return;
            }

            Class<?> genericType = data.elementType();
            String resolvedType = resolverRegistry.getType(genericType, encoding, sqlType) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        if (Map.class.isAssignableFrom(type)) {
            return;
        }

        if (type.isArray()) {
            if (!sqlType.supportsArrays()) {
                return;
            }
            String resolvedType = resolverRegistry.getType(type.getComponentType(), encoding, sqlType) + "[]";
            appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
            return;
        }

        String resolvedType = resolverRegistry.getType(type, encoding, sqlType);

        RepositoryModel<?, ?> metadata;
        if (resolvedType == null && (metadata = GeneratedMetadata.getByEntityClass(type)) != null) {
            FieldModel<?> metadataPrimaryKey = metadata.getPrimaryKey();
            Objects.requireNonNull(metadataPrimaryKey, "Primary key must not be null");
            resolvedType = resolverRegistry.getType(metadataPrimaryKey.type(), encoding, sqlType);
        }

        Objects.requireNonNull(resolvedType, "Unsupported type: " + type);

        appendColumn(joiner, data, fieldBuilder, name, unique, primaryKey, primaryKeysJoiner, relationshipsJoiner, resolvedType);
    }

    private String resolveJsonColumnType(FieldModel<T> data) {
        String override = data.jsonColumnDefinition();
        if (override != null && !override.isBlank()) {
            return override;
        }

        return switch (sqlType) {
            case POSTGRESQL -> "jsonb";
            case MYSQL -> "JSON";
            case SQLITE -> "TEXT";
        };
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

        // Optimization: skip validation check entirely if no validations exist in repository
        if (!hasAnyValidation) {
            return;
        }

        // Add CHECK constraints from @Validate annotations
        ValidationModel validation = data.validation();
        if (validation != null && validation.hasValidation()) {
            String checkConstraint = buildValidationCheckConstraint(data.columnName(), validation);
            if (checkConstraint != null && !checkConstraint.isEmpty()) {
                fieldBuilder.append(" CHECK (").append(checkConstraint).append(')');
            }
        }
    }

    /**
     * Builds a CHECK constraint condition from validation rules.
     *
     * @param columnName the column name
     * @param validation the validation model
     * @return the CHECK constraint condition, or null if no valid rules
     */
    private String buildValidationCheckConstraint(String columnName, ValidationModel validation) {
        if (validation == null || !validation.hasValidation()) {
            return null;
        }

        List<String> conditions = new ArrayList<>();
        Map<String, String> params = validation.params();

        for (Validate.Rule rule : validation.rules()) {
            String condition = translateRuleToCheckConstraint(rule, columnName, params);
            if (condition != null && !condition.isEmpty()) {
                conditions.add(condition);
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return String.join(" AND ", conditions);
    }

    /**
     * Translates a validation rule to a SQL CHECK constraint condition.
     */
    private String translateRuleToCheckConstraint(Validate.Rule rule, String field, Map<String, String> params) {
        String quotedField = sqlType.quoteChar() + field + sqlType.quoteChar();

        return switch (rule) {
            case NOT_NULL -> quotedField + " IS NOT NULL";
            case NOT_EMPTY, REQUIRED -> quotedField + " IS NOT NULL AND LENGTH(CAST(" + quotedField + " AS VARCHAR)) > 0";
            case NOT_BLANK -> quotedField + " IS NOT NULL AND " + quotedField + " NOT REGEXP '^\\s*$'";
            case POSITIVE -> quotedField + " > 0";
            case POSITIVE_OR_ZERO -> quotedField + " >= 0";
            case NEGATIVE -> quotedField + " < 0";
            case NEGATIVE_OR_ZERO -> quotedField + " <= 0";
            case MIN -> {
                String min = params.get("min");
                yield min != null ? quotedField + " >= " + min : null;
            }
            case MAX -> {
                String max = params.get("max");
                yield max != null ? quotedField + " <= " + max : null;
            }
            case MIN_LENGTH -> {
                String min = params.get("min");
                yield min != null ? "LENGTH(CAST(" + quotedField + " AS VARCHAR)) >= " + min : null;
            }
            case MAX_LENGTH -> {
                String max = params.get("max");
                yield max != null ? "LENGTH(CAST(" + quotedField + " AS VARCHAR)) <= " + max : null;
            }
            case RANGE -> {
                String min = params.get("min");
                String max = params.get("max");
                if (min != null && max != null) {
                    yield quotedField + " BETWEEN " + min + " AND " + max;
                } else if (min != null) {
                    yield quotedField + " >= " + min;
                } else if (max != null) {
                    yield quotedField + " <= " + max;
                } else {
                    yield null;
                }
            }
            case PATTERN -> {
                String pattern = params.get("pattern");
                yield pattern != null ? quotedField + " REGEXP '" + escapeSqlString(pattern) + "'" : null;
            }
            case UPPERCASE -> quotedField + " = UPPER(" + quotedField + ")";
            case LOWERCASE -> quotedField + " = LOWER(" + quotedField + ")";
            case FUTURE -> quotedField + " > CURRENT_TIMESTAMP";
            case PAST -> quotedField + " < CURRENT_TIMESTAMP";
            case FUTURE_OR_PRESENT -> quotedField + " >= CURRENT_TIMESTAMP";
            case PAST_OR_PRESENT -> quotedField + " <= CURRENT_TIMESTAMP";
            case DIGITS_ONLY, UNIQUE, CREDIT_CARD, URL, EMAIL, UUID, REFERENCE_EXISTS, ALPHA_ONLY, ALPHANUMERIC -> null;
        };
    }

    /**
     * Escapes single quotes in a string for SQL safety.
     */
    private static String escapeSqlString(String value) {
        return value.replace("'", "''");
    }

    /**
     * Gets SQL type for a Java class, falling back to TEXT for unresolved complex types.
     * This handles nested collections/maps that need JSON storage.
     */
    private String getSqlTypeOrText(Class<?> type) {
        try {
            String sqlType = resolverRegistry.getType(type);
            return sqlType != null ? sqlType : "TEXT";
        } catch (IllegalArgumentException e) {
            // Type not resolvable (e.g., Map.class, List.class) - use TEXT for JSON
            return "TEXT";
        }
    }

    private void addPotentialManyToOne(@NotNull FieldModel<T> data, String name, StringJoiner relationshipsJoiner) {
        if (data.relationshipKind() != RelationshipKind.MANY_TO_ONE && data.relationshipKind() != RelationshipKind.ONE_TO_ONE) return;
        RepositoryModel<?, ?> parent = GeneratedMetadata.getByEntityClass(data.type());
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

    static <T> boolean hasPhysicalColumn(String columnName, RepositoryModel<T, ?> repositoryInformation) {
        for (FieldModel<T> field : repositoryInformation.fields()) {
            if (field == null) continue;
            String col = field.columnName();
            if (col != null && col.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }
}
