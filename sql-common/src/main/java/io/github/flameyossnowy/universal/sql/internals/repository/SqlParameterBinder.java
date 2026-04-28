package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RelationshipKind;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.json.JsonCodec;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.AggregateFilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public final class SqlParameterBinder<T, ID> {
    public void addFilterToPreparedStatement(
        List<FilterOption> filters,
        SQLDatabaseParameters parameters,
        TypeResolverRegistry resolverRegistry,
        RepositoryModel<T, ID> repositoryModel,
        QueryParseEngine.SQLType sqlType
    ) {
        for (FilterOption value : filters) {
            switch (value) {
                case null -> {}
                case SelectOption s -> bindSelectOption(s, parameters, resolverRegistry);
                case JsonSelectOption j -> bindJsonSelectOption(j, parameters, resolverRegistry, repositoryModel, sqlType);
                case AggregateFilterOption a -> bindAggregationSelectOption(a, parameters, resolverRegistry);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void bindAggregationSelectOption(
        AggregateFilterOption value,
        SQLDatabaseParameters parameters,
        TypeResolverRegistry resolverRegistry
    ) {
        // Bind any conditional value inside COUNT_IF / SUM_IF conditions if needed.
        // The SQL parser currently inlines values for aggregation expressions, but the
        // binder can still support parameterized aggregate parsing in the future.
        if (value.condition() instanceof SelectOption(String option, String operator, Object value1) && value1 != null) {
            if ("IN".equalsIgnoreCase(operator) && value1 instanceof Collection<?> list) {
                for (Object item : list) {
                    if (item == null) continue;
                    TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(item.getClass());
                    resolver.insert(parameters, option, item);
                }
            } else {
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value1.getClass());
                resolver.insert(parameters, option, value1);
            }
        }

        // Bind the right-hand-side of the aggregate comparison, if present.
        if (value.value() == null) {
            return;
        }

        if ("IN".equalsIgnoreCase(value.operator()) && value.value() instanceof Collection<?> list) {
            for (Object item : list) {
                if (item == null) continue;
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(item.getClass());
                resolver.insert(parameters, value.field(), item);
            }
            return;
        }

        TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.value().getClass());
        resolver.insert(parameters, value.field(), value.value());
    }

    @SuppressWarnings("unchecked")
    private static void bindSelectOption(SelectOption value, SQLDatabaseParameters parameters, TypeResolverRegistry resolverRegistry) {
        if ("IN".equalsIgnoreCase(value.operator()) && value.value() instanceof Collection<?> list) {
            for (Object item : list) {
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(item.getClass());
                resolver.insert(parameters, value.option(), item);
            }
            return;
        }

        TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.value().getClass());
        resolver.insert(parameters, value.option(), value.value());
    }

    @SuppressWarnings("unchecked")
    private void bindJsonSelectOption(
        JsonSelectOption value,
        SQLDatabaseParameters parameters,
        TypeResolverRegistry resolverRegistry,
        RepositoryModel<T, ID> repositoryModel,
        QueryParseEngine.SQLType sqlType
    ) {
        String indexKey = buildJsonIndexKey(value, repositoryModel, sqlType);

        FieldModel<T> jsonField = repositoryModel.fieldByName(value.field());
        if (jsonField == null) {
            throw new IllegalArgumentException("Unknown field in JSON filter: " + value.field());
        }

        if (value.value() == null) {
            parameters.setNull(indexKey, Object.class);
            return;
        }

        // If the user is filtering by an entire JSON object (not a JSON-path scalar),
        // serialize using the field's JsonCodec.
        Object rawValue = value.value();
        boolean isWholeJsonValue = jsonField.isJson() && jsonField.type().isInstance(rawValue);
        Object bindValue = rawValue;

        JsonCodec<Object> codec = resolverRegistry.getJsonCodecFromSupplier(
                jsonField.jsonCodec(),
                jsonField.jsonCodecSupplier(),
                resolverRegistry.getJsonAdapter());
        if (isWholeJsonValue) {
            bindValue = codec.serialize(rawValue, (Class<Object>) jsonField.type());
        }

        if ("IN".equalsIgnoreCase(value.operator()) && rawValue instanceof Collection<?> list) {
            for (Object item : list) {
                Object itemBind = item;
                if (item != null && jsonField.isJson() && jsonField.type().isInstance(item)) {
                    itemBind = codec.serialize(item, (Class<Object>) jsonField.type());
                }

                TypeResolver<Object> resolver = resolverRegistry.resolve(toClass(itemBind != null ? itemBind.getClass() : Object.class));
                resolver.insert(parameters, indexKey, itemBind);
            }
            return;
        }

        TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(bindValue.getClass());
        resolver.insert(parameters, indexKey, bindValue);
    }

    @Contract("null -> null")
    @SuppressWarnings("unchecked")
    private static <E> Class<E> toClass(Object o) {
        if (o instanceof GenericArrayType ty)
            return (Class<E>) Array.newInstance(toClass(ty.getGenericComponentType()), 0)
                .getClass();
        return (Class<E>) o;
    }

    private String buildJsonIndexKey(
        JsonSelectOption filter,
        RepositoryModel<T, ID> repositoryModel,
        QueryParseEngine.SQLType sqlType
    ) {
        FieldModel<T> field = repositoryModel.fieldByName(filter.field());
        if (field == null) {
            throw new IllegalArgumentException("Unknown field in JSON filter: " + filter.field());
        }

        // Must match QueryParseEngine's generated SQL LHS exactly, because SQLDatabaseParameters
        // parses the WHERE clause and uses the raw LHS string as the parameter name.
        return switch (sqlType) {
            case POSTGRESQL -> field.columnName()
                + " #>> '{"
                + filter.jsonPath().replace("$.", "").replace(".", ",")
                + "}'";
            case MYSQL -> "JSON_UNQUOTE(JSON_EXTRACT("
                + field.columnName()
                + ", '"
                + filter.jsonPath()
                + "'))";
            case SQLITE -> throw new UnsupportedOperationException(
                "SQLite does not support JSON filtering in Universal"
            );
        };
    }

    @SuppressWarnings("unchecked")
    public void setUpdateParameters(SQLDatabaseParameters statement, @NotNull T entity, RepositoryModel<T, ID> repositoryModel, TypeResolverRegistry resolverRegistry) {
        java.util.Set<String> versionColumns = resolveJsonVersionColumns(repositoryModel);

        for (FieldModel<T> fieldData : repositoryModel.fields()) {
            if (fieldData.autoIncrement() || fieldData.relationshipKind() == RelationshipKind.ONE_TO_MANY) {
                continue;
            }

            // @JsonVersioned: UpdateSqlBuilder generates "<versionCol> = <versionCol> + 1" (no placeholder),
            // so we must NOT bind that *version column* as a normal SET parameter.
            if (versionColumns.contains(fieldData.columnName())) {
                continue;
            }

            T value = (T) fieldData.getValue(entity);

            if (fieldData.hasNowAnnotation()) {
                Object nowValue = nowValue(fieldData.type());
                if (nowValue != null) {
                    //noinspection unchecked
                    value = (T) nowValue;
                    fieldData.setValue(entity, value);
                }
            }
            T finalValue1 = value;
            Logging.deepInfo(() -> "Processing field for update: " + fieldData.name() + " with value: " + finalValue1);

            if ((fieldData.relationshipKind() == RelationshipKind.ONE_TO_ONE || fieldData.relationshipKind() == RelationshipKind.MANY_TO_ONE)) {
                if (value == null) {
                    Logging.deepInfo(() -> "  -> Relationship field is null, setting to NULL");
                } else {
                    RepositoryModel<T, ID> relatedInfo = (RepositoryModel<T, ID>) GeneratedMetadata.getByEntityClass(fieldData.type());
                    if (relatedInfo != null) {
                        FieldModel<T> pkField = relatedInfo.getPrimaryKey();
                        if (pkField == null) {
                            throw new IllegalArgumentException("Cannot extract primary key from " + repositoryModel.tableName() + " because there's no id.");
                        }

                        value = (T) pkField.getValue(value);
                        T finalValue = value;
                        Logging.deepInfo(() -> "  -> Relationship field, using ID for update: " + finalValue);
                    }
                }
            }

            TypeResolver<Object> resolver;
            if ((fieldData.relationshipKind() == RelationshipKind.ONE_TO_ONE || fieldData.relationshipKind() == RelationshipKind.MANY_TO_ONE) && value != null) {
                RepositoryModel<T, ID> relatedInfo = (RepositoryModel<T, ID>) GeneratedMetadata.getByEntityClass(fieldData.type());
                if (relatedInfo != null) {
                    FieldModel<T> pkField = relatedInfo.getPrimaryKey();
                    if (pkField == null) {
                        throw new IllegalArgumentException("Cannot extract primary key from " + repositoryModel.tableName() + " because there's no id.");
                    }

                    resolver = (TypeResolver<Object>) resolverRegistry.resolve(pkField.type());
                } else {
                    resolver = (TypeResolver<Object>) resolverRegistry.resolve(fieldData.type());
                }
            } else {
                resolver = (TypeResolver<Object>) resolverRegistry.resolve(fieldData.type());
            }

            if (resolver == null) {
                // Some types (notably @JsonField payload objects) are intentionally not registered in the TypeResolverRegistry.
                // In those cases we still need to bind the parameter so SQLDatabaseParameters can perform JSON serialization.
                if (fieldData.isJson()) {
                    Logging.deepInfo(() -> "No resolver for JSON field " + fieldData.type() + ", binding via SQLDatabaseParameters.set");
                    statement.set(fieldData.name(), value, fieldData.type());
                    continue;
                }

                Logging.deepInfo(() -> "No resolver for " + fieldData.type() + ", assuming it's a relationship handled elsewhere.");
                continue;
            }

            T finalValue2 = value;
            Logging.deepInfo(() -> "Binding parameter " + fieldData.name() + ": " + finalValue2 + " (type: " + (finalValue2 != null ? finalValue2.getClass().getSimpleName() : "null") + ")");
            resolver.insert(statement, fieldData.name(), value);
        }

        // Bind WHERE primary key (+ optimistic-lock version checks).
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key must not be null");
        }

        Object id = primaryKey.getValue(entity);
        TypeResolver<Object> idResolver = (TypeResolver<Object>) resolverRegistry.resolve(primaryKey.type());
        idResolver.insert(statement, primaryKey.columnName(), id);

        for (FieldModel<T> jsonField : repositoryModel.fields()) {
            if (jsonField == null || !jsonField.isJson() || !jsonField.jsonVersioned()) {
                continue;
            }

            String versionColumn = jsonField.columnName() + "_version";
            FieldModel<T> companion = findFieldByColumnName(repositoryModel, versionColumn);
            if (companion == null) {
                throw new IllegalStateException(
                    "@JsonVersioned requires a companion field with column name '" + versionColumn + "' on entity "
                        + repositoryModel.entitySimpleName()
                );
            }

            Object expected = companion.getValue(entity);
            if (expected == null) {
                throw new IllegalStateException(
                    "Optimistic-lock version value for column '" + versionColumn + "' is null on entity "
                        + repositoryModel.entitySimpleName()
                );
            }

            TypeResolver<Object> vResolver = (TypeResolver<Object>) resolverRegistry.resolve(companion.type());
            vResolver.insert(statement, versionColumn, expected);
        }
    }

    private static <T, ID> java.util.Set<String> resolveJsonVersionColumns(RepositoryModel<T, ID> repositoryModel) {
        java.util.Set<String> cols = new java.util.HashSet<>(2);
        for (FieldModel<T> field : repositoryModel.fields()) {
            if (field == null || !field.isJson() || !field.jsonVersioned()) {
                continue;
            }
            cols.add(field.columnName() + "_version");
        }
        return cols;
    }

    private static <T, ID> @Nullable FieldModel<T> findFieldByColumnName(
        RepositoryModel<T, ID> repositoryModel,
        String columnName
    ) {
        for (FieldModel<T> field : repositoryModel.fields()) {
            if (field == null) continue;
            if (field.columnName() != null && field.columnName().equalsIgnoreCase(columnName)) {
                return field;
            }
        }
        return null;
    }

    private static @Nullable Object nowValue(@NotNull Class<?> type) {
        if (type == Instant.class) {
            return Instant.now();
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.now();
        }
        if (type == LocalDate.class) {
            return LocalDate.now();
        }
        if (type == ZonedDateTime.class) {
            return ZonedDateTime.now();
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.now();
        }
        if (type == Timestamp.class) {
            return new Timestamp(System.currentTimeMillis());
        }
        if (type == Date.class) {
            return new Date();
        }
        if (type == java.sql.Date.class) {
            return new java.sql.Date(System.currentTimeMillis());
        }
        if (type == java.sql.Time.class) {
            return new java.sql.Time(System.currentTimeMillis());
        }
        if (type == Year.class) {
            return Year.now();
        }
        if (type == YearMonth.class) {
            return YearMonth.now();
        }
        if (type == MonthDay.class) {
            return MonthDay.now();
        }
        if (type == Calendar.class) {
            return Calendar.getInstance();
        }

        // Fallback: if someone uses a temporal type we don't recognize, don't mutate.
        return null;
    }

    @SuppressWarnings("unchecked")
    public void setUpdateParameters(
        @NotNull UpdateQuery query,
        SQLDatabaseParameters parameters,
        TypeResolverRegistry resolverRegistry,
        RepositoryModel<T, ID> repositoryModel,
        QueryParseEngine.SQLType sqlType
    ) {
        for (var value : query.updates().entrySet()) {
            TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.getValue().getClass());
            resolver.insert(parameters, value.getKey(), value.getValue());
        }

        List<FilterOption> conditions = query.filters();
        if (conditions.isEmpty()) return;
        for (FilterOption value : conditions) {
            bindOptionValue(parameters, resolverRegistry, repositoryModel, sqlType, value);
        }
    }

    private void bindOptionValue(SQLDatabaseParameters parameters, TypeResolverRegistry resolverRegistry, RepositoryModel<T, ID> repositoryModel, QueryParseEngine.SQLType sqlType, FilterOption value) {
        switch (value) {
            case SelectOption s -> {
                bindSelectOption(s, parameters, resolverRegistry);
                return;
            }
            case JsonSelectOption j -> {
                bindJsonSelectOption(j, parameters, resolverRegistry, repositoryModel, sqlType);
                return;
            }
            case null, default -> {
            }
        }

        throw new IllegalStateException("Unknown filter type: " + value);
    }

    public void setUpdateParameters(
        @NotNull DeleteQuery query,
        SQLDatabaseParameters parameters,
        TypeResolverRegistry resolverRegistry,
        RepositoryModel<T, ID> repositoryModel,
        QueryParseEngine.SQLType sqlType
    ) {
        for (FilterOption value : query.filters()) {
            bindOptionValue(parameters, resolverRegistry, repositoryModel, sqlType, value);
        }
    }
}
