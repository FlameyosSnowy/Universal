package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parses aggregation queries into SQL.
 * Handles GROUP BY, HAVING, and complex aggregation functions.
 */
public class AggregationQueryParser<T, ID> {
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final QueryParseEngine.SQLType sqlType;
    private final SubQueryParser<T, ID> subQueryParser;

    public AggregationQueryParser(
        RepositoryModel<T, ID> repositoryModel,
        TypeResolverRegistry resolverRegistry,
        QueryParseEngine.SQLType sqlType) {
        this.repositoryModel = repositoryModel;
        this.resolverRegistry = resolverRegistry;
        this.sqlType = sqlType;
        this.subQueryParser = new SubQueryParser<>(repositoryModel, resolverRegistry, sqlType);
    }

    public record BoundSql(String sql, List<String> paramNames, List<Object> paramValues) {
        /** Convenience factory for the no-parameter case. */
        public static BoundSql of(String sql) {
            return new BoundSql(sql, List.of(), List.of());
        }
    }

    /**
     * Parse an aggregation query into SQL with inlined literal values (no bind
     * parameters).  Kept for backwards-compatible callers that don't need
     * parameterized execution.
     *
     * <pre>
     * SELECT status, COUNT(*) as count
     * FROM users
     * WHERE active = true
     * GROUP BY status
     * HAVING COUNT(*) > 10
     * ORDER BY count DESC
     * </pre>
     */
    public String parse(AggregationQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ");

        appendSelectClause(sql, query);

        sql.append(" FROM ")
            .append(sqlType.quoteChar())
            .append(repositoryModel.tableName())
            .append(sqlType.quoteChar());

        if (!query.whereFilters().isEmpty()) {
            sql.append(" WHERE ").append(parseFilters(query.whereFilters()));
        }

        appendGroupBy(sql, query);

        if (!query.havingFilters().isEmpty()) {
            sql.append(" HAVING ").append(parseHavingFilters(query.havingFilters()));
        }

        appendOrderBy(sql, query);

        if (query.limit() >= 0) {
            sql.append(" LIMIT ").append(query.limit());
        }

        return sql.toString();
    }

    /**
     * Parse an aggregation query into a {@link BoundSql} that carries both the
     * SQL string and the ordered bind-parameter metadata ({@code paramNames} +
     * {@code paramValues}) for each {@code ?} placeholder.
     */
    public BoundSql parseParameterized(AggregationQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> paramNames  = new ArrayList<>(8);
        List<Object> paramValues = new ArrayList<>(8);

        appendSelectClause(sql, query);

        sql.append(" FROM ")
            .append(sqlType.quoteChar())
            .append(repositoryModel.tableName())
            .append(sqlType.quoteChar());

        if (!query.whereFilters().isEmpty()) {
            sql.append(" WHERE ");
            appendFiltersParameterized(sql, query.whereFilters(), paramNames, paramValues, false);
        }

        appendGroupBy(sql, query);

        if (!query.havingFilters().isEmpty()) {
            sql.append(" HAVING ");
            appendFiltersParameterized(sql, query.havingFilters(), paramNames, paramValues, true);
        }

        appendOrderBy(sql, query);

        if (query.limit() > 0) {
            sql.append(" LIMIT ").append(query.limit());
        }

        return new BoundSql(sql.toString(), List.copyOf(paramNames), List.copyOf(paramValues));
    }

    // -----------------------------------------------------------------------
    // SELECT clause
    // -----------------------------------------------------------------------

    private void appendSelectClause(StringBuilder sql, AggregationQuery query) {
        if (query.selectFields().isEmpty()) {
            sql.append("*");
            return;
        }
        boolean seen = false;
        for (FieldDefinition fieldDefinition : query.selectFields()) {
            if (seen) sql.append(", "); else seen = true;
            sql.append(parseFieldDefinition(fieldDefinition));
        }
    }

    // -----------------------------------------------------------------------
    // GROUP BY / ORDER BY helpers (shared between parse and parseParameterized)
    // -----------------------------------------------------------------------

    private void appendGroupBy(StringBuilder sql, AggregationQuery query) {
        if (query.groupByFields().isEmpty()) return;
        sql.append(" GROUP BY ");
        boolean seen = false;
        for (String f : query.groupByFields()) {
            if (seen) sql.append(", "); else seen = true;
            sql.append(f);
        }
    }

    private void appendOrderBy(StringBuilder sql, AggregationQuery query) {
        if (query.orderBy().isEmpty()) return;
        sql.append(" ORDER BY ");
        boolean seen = false;
        for (SortOption s : query.orderBy()) {
            if (seen) sql.append(", "); else seen = true;
            sql.append(s.field()).append(' ').append(switch (s.order()) {
                case ASCENDING  -> "ASC";
                case DESCENDING -> "DESC";
            });
        }
    }

    // -----------------------------------------------------------------------
    // Field definitions
    // -----------------------------------------------------------------------

    private String parseFieldDefinition(FieldDefinition field) {
        if (field instanceof SimpleFieldDefinition simple)               return parseSimpleField(simple);
        if (field instanceof AggregateFieldDefinition agg)               return parseAggregateField(agg);
        if (field instanceof WindowFieldDefinition window)               return parseWindowField(window);
        if (field instanceof SubQuery.SubQueryFieldDefinition subQuery)  return parseSubQueryField(subQuery);
        if (field instanceof QueryField<?> queryField)                   return parseQueryField(queryField);
        throw new IllegalArgumentException("Unknown field definition type: " + field.getClass());
    }

    private String parseSimpleField(SimpleFieldDefinition field) {
        String sql = field.getFieldName();
        if (field.alias() != null && !field.alias().equals(field.getFieldName())) sql += " AS " + field.alias();
        return sql;
    }

    private String parseQueryField(QueryField<?> field) {
        String sql = field.getFieldName();
        if (field.getAlias() != null && !field.getAlias().equals(field.getFieldName())) sql += " AS " + field.getAlias();
        return sql;
    }

    private String parseAggregateField(AggregateFieldDefinition agg) {
        String fieldExpr = buildAggregateExpression(agg);
        if (agg.alias() != null) fieldExpr += " AS " + agg.alias();
        return fieldExpr;
    }

    private String buildAggregateExpression(AggregateFieldDefinition agg) {
        String field = agg.field();
        return switch (agg.aggregationType()) {
            case COUNT         -> "COUNT(*)";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case COUNT_IF      -> {
                if (agg.condition() == null) throw new IllegalArgumentException("COUNT_IF requires a condition");
                yield "COUNT(CASE WHEN " + parseCondition(field, agg.condition()) + " THEN 1 END)";
            }
            case SUM           -> "SUM(" + field + ")";
            case SUM_IF        -> {
                if (agg.condition() == null) throw new IllegalArgumentException("SUM_IF requires a condition");
                yield "SUM(CASE WHEN " + parseCondition(field, agg.condition()) + " THEN " + field + " ELSE 0 END)";
            }
            case AVG           -> "AVG(" + field + ")";
            case MIN           -> "MIN(" + field + ")";
            case MAX           -> "MAX(" + field + ")";
            case STRING_AGG    -> {
                String delimiter = agg.condition() instanceof SelectOption sel ? String.valueOf(sel.value()) : ",";
                yield sqlType == QueryParseEngine.SQLType.POSTGRESQL
                    ? "STRING_AGG(" + field + ", '" + delimiter + "')"
                    : "GROUP_CONCAT(" + field + " SEPARATOR '" + delimiter + "')";
            }
            case ARRAY_LENGTH  -> sqlType == QueryParseEngine.SQLType.POSTGRESQL
                ? "jsonb_array_length(" + field + ")" : "JSON_LENGTH(" + field + ")";
            case JSON_ARRAY_AGG -> sqlType == QueryParseEngine.SQLType.POSTGRESQL
                ? "json_agg(" + field + ")" : "JSON_ARRAYAGG(" + field + ")";
            case JSON_OBJECT_AGG -> {
                String valueField = agg.condition() instanceof SelectOption sel ? String.valueOf(sel.value()) : "value";
                yield sqlType == QueryParseEngine.SQLType.POSTGRESQL
                    ? "json_object_agg(" + field + ", " + valueField + ")"
                    : "JSON_OBJECTAGG(" + field + ", " + valueField + ")";
            }
            case STDDEV        -> "STDDEV(" + field + ")";
            case VARIANCE      -> "VARIANCE(" + field + ")";
            case FIRST         -> "FIRST_VALUE(" + field + ")";
            case LAST          -> "LAST_VALUE(" + field + ")";
        };
    }

    private String buildAggregateExpressionParameterized(
        AggregateFieldDefinition agg,
        List<String> paramNames,
        List<Object> paramValues
    ) {
        String field = agg.field();
        return switch (agg.aggregationType()) {
            case COUNT         -> "COUNT(*)";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case COUNT_IF      -> {
                if (agg.condition() == null) throw new IllegalArgumentException("COUNT_IF requires a condition");
                yield "COUNT(CASE WHEN " + parseConditionParameterized(field, agg.condition(), paramNames, paramValues) + " THEN 1 END)";
            }
            case SUM           -> "SUM(" + field + ")";
            case SUM_IF        -> {
                if (agg.condition() == null) throw new IllegalArgumentException("SUM_IF requires a condition");
                yield "SUM(CASE WHEN " + parseConditionParameterized(field, agg.condition(), paramNames, paramValues) + " THEN " + field + " ELSE 0 END)";
            }
            case AVG           -> "AVG(" + field + ")";
            case MIN           -> "MIN(" + field + ")";
            case MAX           -> "MAX(" + field + ")";
            case STRING_AGG    -> {
                String delimiter = agg.condition() instanceof SelectOption sel ? String.valueOf(sel.value()) : ",";
                yield sqlType == QueryParseEngine.SQLType.POSTGRESQL
                    ? "STRING_AGG(" + field + ", '" + delimiter + "')"
                    : "GROUP_CONCAT(" + field + " SEPARATOR '" + delimiter + "')";
            }
            case ARRAY_LENGTH  -> sqlType == QueryParseEngine.SQLType.POSTGRESQL
                ? "jsonb_array_length(" + field + ")" : "JSON_LENGTH(" + field + ")";
            case JSON_ARRAY_AGG -> sqlType == QueryParseEngine.SQLType.POSTGRESQL
                ? "json_agg(" + field + ")" : "JSON_ARRAYAGG(" + field + ")";
            case JSON_OBJECT_AGG -> {
                String valueField = agg.condition() instanceof SelectOption sel ? String.valueOf(sel.value()) : "value";
                yield sqlType == QueryParseEngine.SQLType.POSTGRESQL
                    ? "json_object_agg(" + field + ", " + valueField + ")"
                    : "JSON_OBJECTAGG(" + field + ", " + valueField + ")";
            }
            case STDDEV        -> "STDDEV(" + field + ")";
            case VARIANCE      -> "VARIANCE(" + field + ")";
            case FIRST         -> "FIRST_VALUE(" + field + ")";
            case LAST          -> "LAST_VALUE(" + field + ")";
        };
    }

    // -----------------------------------------------------------------------
    // Condition helpers
    // -----------------------------------------------------------------------

    private String parseCondition(String field, FilterOption condition) {
        if (condition instanceof SelectOption select) {
            return field + " " + select.operator() + " " + formatValue(select.value());
        }
        throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass());
    }

    private String parseConditionParameterized(
        String field,
        FilterOption condition,
        List<String> paramNames,
        List<Object> paramValues
    ) {
        if (condition instanceof SelectOption(String option, String operator, Object value)) {
            StringBuilder out = new StringBuilder(32);
            appendSelectOptionParameterized(out, field, operator, value, paramNames, paramValues);
            return out.toString();
        }
        throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass());
    }

    // -----------------------------------------------------------------------
    // Window functions
    // -----------------------------------------------------------------------

    private String parseWindowField(WindowFieldDefinition window) {
        StringBuilder sql = new StringBuilder();
        sql.append(window.functionType().name());

        if (isAggregateWindowFunction(window.functionType())) {
            sql.append("(").append(window.field()).append(")");
        } else {
            sql.append("()");
        }

        sql.append(" OVER (");

        if (!window.partitionBy().isEmpty()) {
            sql.append("PARTITION BY ").append(String.join(", ", window.partitionBy()));
        }

        if (!window.orderBy().isEmpty()) {
            if (!window.partitionBy().isEmpty()) sql.append(" ");
            sql.append("ORDER BY ");
            boolean seen = false;
            for (SortOption s : window.orderBy()) {
                if (seen) sql.append(", "); else seen = true;
                sql.append(s.field()).append(' ').append(s.order().name());
            }
        }

        if (window.frameStart() != null && window.frameEnd() != null) {
            sql.append(" ROWS BETWEEN ").append(window.frameStart()).append(" AND ").append(window.frameEnd());
        }

        sql.append(")");

        if (window.alias() != null) sql.append(" AS ").append(window.alias());

        return sql.toString();
    }

    private boolean isAggregateWindowFunction(WindowFunctionType type) {
        return type == WindowFunctionType.COUNT || type == WindowFunctionType.SUM
            || type == WindowFunctionType.AVG   || type == WindowFunctionType.MIN
            || type == WindowFunctionType.MAX;
    }

    // -----------------------------------------------------------------------
    // Sub-query field
    // -----------------------------------------------------------------------

    private String parseSubQueryField(SubQuery.SubQueryFieldDefinition subQueryField) {
        String sql = "(" + subQueryParser.parse(subQueryField.subQuery()) + ")";
        if (subQueryField.alias() != null) sql += " AS " + subQueryField.alias();
        return sql;
    }

    // -----------------------------------------------------------------------
    // Literal filter rendering (non-parameterized path)
    // -----------------------------------------------------------------------

    private String parseFilters(List<FilterOption> filters) {
        StringBuilder out = new StringBuilder(filters.size() * 16);
        boolean seen = false;
        for (FilterOption filter : filters) {
            if (seen) out.append(" AND "); else seen = true;
            out.append(parseFilter(filter));
        }
        return out.toString();
    }

    private String parseHavingFilters(List<FilterOption> filters) {
        StringBuilder out = new StringBuilder(filters.size() * 16);
        boolean seen = false;
        for (FilterOption filter : filters) {
            if (seen) out.append(" AND "); else seen = true;
            out.append(parseHavingFilter(filter));
        }
        return out.toString();
    }

    private String parseFilter(FilterOption filter) {
        if (filter instanceof SelectOption(String option, String operator, Object value)) {
            return option + " " + operator + " " + formatValue(value);
        }
        if (filter instanceof AggregateFilterOption agg) {
            return buildAggregateExpression(new AggregateFieldDefinition(agg.field(), agg.jsonPath(), agg.aggregationType(), agg.condition(), null))
                + " " + agg.operator() + " " + formatValue(agg.value());
        }
        throw new IllegalArgumentException("Unknown filter type: " + filter.getClass());
    }

    private String parseHavingFilter(FilterOption filter) {
        if (filter instanceof AggregateFilterOption agg) {
            return buildAggregateExpression(new AggregateFieldDefinition(agg.field(), agg.jsonPath(), agg.aggregationType(), agg.condition(), null))
                + " " + agg.operator() + " " + formatValue(agg.value());
        }
        return parseFilter(filter);
    }

    // -----------------------------------------------------------------------
    // Parameterized filter rendering
    // -----------------------------------------------------------------------

    private void appendFiltersParameterized(
        StringBuilder out,
        List<FilterOption> filters,
        List<String> paramNames,
        List<Object> paramValues,
        boolean having
    ) {
        boolean seen = false;
        for (FilterOption filter : filters) {
            if (seen) out.append(" AND "); else seen = true;
            appendFilterParameterized(out, filter, paramNames, paramValues, having);
        }
    }

    private void appendFilterParameterized(
        StringBuilder out,
        FilterOption filter,
        List<String> paramNames,
        List<Object> paramValues,
        boolean having
    ) {
        if (filter instanceof SelectOption(String option, String operator, Object value)) {
            appendSelectOptionParameterized(out, option, operator, value, paramNames, paramValues);
            return;
        }

        if (filter instanceof AggregateFilterOption agg) {
            String aggExpr = buildAggregateExpressionParameterized(
                new AggregateFieldDefinition(agg.field(), agg.jsonPath(), agg.aggregationType(), agg.condition(), null),
                paramNames, paramValues
            );
            appendSelectOptionParameterized(out, aggExpr, agg.operator(), agg.value(), paramNames, paramValues);
            return;
        }

        throw new IllegalArgumentException("Unknown filter type: " + filter.getClass());
    }

    private void appendSelectOptionParameterized(
        StringBuilder out,
        String lhs,
        String operator,
        Object value,
        List<String> paramNames,
        List<Object> paramValues
    ) {
        if (value == null) {
            if ("=".equals(operator) || "==".equals(operator)) {
                out.append(lhs).append(" IS NULL");
            } else if ("!=".equals(operator) || "<>".equals(operator)) {
                out.append(lhs).append(" IS NOT NULL");
            } else {
                out.append(lhs).append(' ').append(operator).append(" NULL");
            }
            return;
        }

        if ("IN".equalsIgnoreCase(operator) && value instanceof Collection<?> list) {
            out.append(lhs).append(" IN (");
            boolean seen = false;
            for (Object v : list) {
                if (seen) out.append(", "); else seen = true;
                out.append('?');
                paramNames.add(lhs);
                paramValues.add(v);
            }
            out.append(')');
            return;
        }

        out.append(lhs).append(' ').append(operator).append(" ?");
        paramNames.add(lhs);
        paramValues.add(value);
    }

    // -----------------------------------------------------------------------
    // Literal value formatting (non-parameterized path)
    // -----------------------------------------------------------------------

    private String formatValue(Object value) {
        if (value == null)                                   return "NULL";
        if (value instanceof String s)                       return "'" + s.replace("'", "''") + "'";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "'" + value + "'";
    }
}