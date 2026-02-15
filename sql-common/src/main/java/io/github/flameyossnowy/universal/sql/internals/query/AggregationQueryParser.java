package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.Collection;
import java.util.ArrayList;
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

    public record ParameterizedSql(String sql, List<Object> params) {}

    /**
     * Parse an aggregation query into SQL.
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

        // Build SELECT clause
        if (query.selectFields().isEmpty()) {
            sql.append("*");
        } else {
            boolean seen = false;
            for (FieldDefinition fieldDefinition : query.selectFields()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(parseFieldDefinition(fieldDefinition));
            }
        }

        // FROM clause
        sql.append(" FROM ")
            .append(sqlType.quoteChar())
            .append(repositoryModel.tableName())
            .append(sqlType.quoteChar());

        // WHERE clause
        if (!query.whereFilters().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(parseFilters(query.whereFilters()));
        }

        // GROUP BY clause
        if (!query.groupByFields().isEmpty()) {
            sql.append(" GROUP BY ");
            boolean seen = false;
            for (String f : query.groupByFields()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(f);
            }
        }

        // HAVING clause
        if (!query.havingFilters().isEmpty()) {
            sql.append(" HAVING ");
            sql.append(parseHavingFilters(query.havingFilters()));
        }

        // ORDER BY clause
        if (!query.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            boolean seen = false;
            for (SortOption s : query.orderBy()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(s.field()).append(' ').append(s.order().name());
            }
        }

        // LIMIT clause
        if (query.limit() >= 0) {
            sql.append(" LIMIT ").append(query.limit());
        }

        return sql.toString();
    }

    public ParameterizedSql parseParameterized(AggregationQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ");
        ArrayList<Object> params = new ArrayList<>(8);

        if (query.selectFields().isEmpty()) {
            sql.append("*");
        } else {
            boolean seen = false;
            for (FieldDefinition fieldDefinition : query.selectFields()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(parseFieldDefinition(fieldDefinition));
            }
        }

        sql.append(" FROM ")
            .append(sqlType.quoteChar())
            .append(repositoryModel.tableName())
            .append(sqlType.quoteChar());

        if (!query.whereFilters().isEmpty()) {
            sql.append(" WHERE ");
            appendFiltersParameterized(sql, query.whereFilters(), params, false);
        }

        if (!query.groupByFields().isEmpty()) {
            sql.append(" GROUP BY ");
            boolean seen = false;
            for (String f : query.groupByFields()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(f);
            }
        }

        if (!query.havingFilters().isEmpty()) {
            sql.append(" HAVING ");
            appendFiltersParameterized(sql, query.havingFilters(), params, true);
        }

        if (!query.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            boolean seen = false;
            for (SortOption s : query.orderBy()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(s.field()).append(' ').append(switch (s.order()) {
                    case ASCENDING -> "ASC";
                    case DESCENDING -> "DESC";
                });
            }
        }

        if (query.limit() > 0) {
            sql.append(" LIMIT ").append(query.limit());
        }

        return new ParameterizedSql(sql.toString(), params);
    }

    /**
     * Parse a field definition (simple field, aggregate, or window function).
     */
    private String parseFieldDefinition(FieldDefinition field) {
        if (field instanceof SimpleFieldDefinition simple) {
            return parseSimpleField(simple);
        } else if (field instanceof AggregateFieldDefinition agg) {
            return parseAggregateField(agg);
        } else if (field instanceof WindowFieldDefinition window) {
            return parseWindowField(window);
        } else if (field instanceof SubQuery.SubQueryFieldDefinition subQuery) {
            return parseSubQueryField(subQuery);
        } else if (field instanceof QueryField<?> queryField) {
            return parseQueryField(queryField);
        }
        throw new IllegalArgumentException("Unknown field definition type: " + field.getClass());
    }

    private String parseSimpleField(SimpleFieldDefinition field) {
        String sql = field.getFieldName();
        if (field.alias() != null && !field.alias().equals(field.getFieldName())) {
            sql += " AS " + field.alias();
        }
        return sql;
    }

    private String parseQueryField(QueryField<?> field) {
        String sql = field.getFieldName();
        if (field.getAlias() != null && !field.getAlias().equals(field.getFieldName())) {
            sql += " AS " + field.getAlias();
        }
        return sql;
    }

    private String parseAggregateField(AggregateFieldDefinition agg) {
        String fieldExpr = buildAggregateExpression(agg);
        if (agg.alias() != null) {
            fieldExpr += " AS " + agg.alias();
        }
        return fieldExpr;
    }

    /**
     * Build aggregate expression like:
     * - COUNT(*)
     * - COUNT(DISTINCT role)
     * - COUNT(CASE WHEN status = 'active' THEN 1 END)
     * - AVG(salary)
     * - jsonb_array_length(tasks)
     */
    private String buildAggregateExpression(AggregateFieldDefinition agg) {
        String field = agg.field();
        
        switch (agg.aggregationType()) {
            case COUNT:
                return "COUNT(*)";
                
            case COUNT_DISTINCT:
                return "COUNT(DISTINCT " + field + ")";
                
            case COUNT_IF:
                // COUNT(CASE WHEN condition THEN 1 END)
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("COUNT_IF requires a condition");
                }
                return "COUNT(CASE WHEN " + parseCondition(field, agg.condition()) + " THEN 1 END)";
                
            case SUM:
                return "SUM(" + field + ")";
                
            case SUM_IF:
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("SUM_IF requires a condition");
                }
                return "SUM(CASE WHEN " + parseCondition(field, agg.condition()) + " THEN " + field + " ELSE 0 END)";
                
            case AVG:
                return "AVG(" + field + ")";
                
            case MIN:
                return "MIN(" + field + ")";
                
            case MAX:
                return "MAX(" + field + ")";
                
            case STRING_AGG:
                // PostgreSQL: STRING_AGG(field, delimiter)
                // MySQL: GROUP_CONCAT(field SEPARATOR delimiter)
                String delimiter = agg.condition() instanceof SelectOption sel ? 
                    String.valueOf(sel.value()) : ",";
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) {
                    return "STRING_AGG(" + field + ", '" + delimiter + "')";
                } else {
                    return "GROUP_CONCAT(" + field + " SEPARATOR '" + delimiter + "')";
                }
                
            case ARRAY_LENGTH:
                // PostgreSQL: jsonb_array_length(field)
                // MySQL: JSON_LENGTH(field)
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) {
                    return "jsonb_array_length(" + field + ")";
                } else {
                    return "JSON_LENGTH(" + field + ")";
                }
                
            case JSON_ARRAY_AGG:
                // PostgreSQL: json_agg(field)
                // MySQL: JSON_ARRAYAGG(field)
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) {
                    return "json_agg(" + field + ")";
                } else {
                    return "JSON_ARRAYAGG(" + field + ")";
                }
                
            case JSON_OBJECT_AGG:
                // PostgreSQL: json_object_agg(key_field, value_field)
                String valueField = agg.condition() instanceof SelectOption sel ?
                    String.valueOf(sel.value()) : "value";
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) {
                    return "json_object_agg(" + field + ", " + valueField + ")";
                } else {
                    return "JSON_OBJECTAGG(" + field + ", " + valueField + ")";
                }
                
            case STDDEV:
                return "STDDEV(" + field + ")";
                
            case VARIANCE:
                return "VARIANCE(" + field + ")";
                
            case FIRST:
                // PostgreSQL: (array_agg(field ORDER BY ...))[1]
                // MySQL: Use window function FIRST_VALUE
                return "FIRST_VALUE(" + field + ")";
                
            case LAST:
                return "LAST_VALUE(" + field + ")";
                
            default:
                throw new IllegalArgumentException("Unsupported aggregation type: " + agg.aggregationType());
        }
    }

    /**
     * Parse condition for CASE WHEN clause.
     */
    private String parseCondition(String field, FilterOption condition) {
        if (condition instanceof SelectOption select) {
            String operator = select.operator();
            Object value = select.value();
            
            // Handle different operators
            String valueStr = formatValue(value);
            return field + " " + operator + " " + valueStr;
        }
        
        throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass());
    }

    private String buildAggregateExpressionParameterized(AggregateFieldDefinition agg, ArrayList<Object> params) {
        String field = agg.field();

        return switch (agg.aggregationType()) {
            case COUNT -> "COUNT(*)";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case COUNT_IF -> {
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("COUNT_IF requires a condition");
                }
                yield "COUNT(CASE WHEN " + parseConditionParameterized(field, agg.condition(), params) + " THEN 1 END)";
            }
            case SUM -> "SUM(" + field + ")";
            case SUM_IF -> {
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("SUM_IF requires a condition");
                }
                yield "SUM(CASE WHEN " + parseConditionParameterized(field, agg.condition(), params) + " THEN " + field + " ELSE 0 END)";
            }
            case AVG -> "AVG(" + field + ")";
            case MIN -> "MIN(" + field + ")";
            case MAX -> "MAX(" + field + ")";
            case STRING_AGG -> {
                String delimiter = agg.condition() instanceof SelectOption sel ? String.valueOf(sel.value()) : ",";
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) {
                    yield "STRING_AGG(" + field + ", '" + delimiter + "')";
                }
                yield "GROUP_CONCAT(" + field + " SEPARATOR '" + delimiter + "')";
            }
            case ARRAY_LENGTH -> sqlType == QueryParseEngine.SQLType.POSTGRESQL
                ? "jsonb_array_length(" + field + ")"
                : "JSON_LENGTH(" + field + ")";
            case JSON_ARRAY_AGG -> sqlType == QueryParseEngine.SQLType.POSTGRESQL
                ? "json_agg(" + field + ")"
                : "JSON_ARRAYAGG(" + field + ")";
            case JSON_OBJECT_AGG -> {
                String valueField = agg.condition() instanceof SelectOption sel ? String.valueOf(sel.value()) : "value";
                if (sqlType == QueryParseEngine.SQLType.POSTGRESQL) {
                    yield "json_object_agg(" + field + ", " + valueField + ")";
                }
                yield "JSON_OBJECTAGG(" + field + ", " + valueField + ")";
            }
            case STDDEV -> "STDDEV(" + field + ")";
            case VARIANCE -> "VARIANCE(" + field + ")";
            case FIRST -> "FIRST_VALUE(" + field + ")";
            case LAST -> "LAST_VALUE(" + field + ")";
        };
    }

    private String parseConditionParameterized(String field, FilterOption condition, ArrayList<Object> params) {
        if (condition instanceof SelectOption(String option, String operator, Object value)) {
            // Option is ignored here; condition always applies to the aggregate field expression.
            StringBuilder out = new StringBuilder(32);
            appendSelectOptionParameterized(out, field, operator, value, params);
            return out.toString();
        }

        throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass());
    }

    /**
     * Parse window function field.
     */
    private String parseWindowField(WindowFieldDefinition window) {
        StringBuilder sql = new StringBuilder();
        
        // Function name
        sql.append(window.functionType().name());
        
        // Function arguments (for aggregate window functions)
        if (isAggregateWindowFunction(window.functionType())) {
            sql.append("(").append(window.field()).append(")");
        } else {
            sql.append("()");
        }
        
        // OVER clause
        sql.append(" OVER (");
        
        // PARTITION BY
        if (!window.partitionBy().isEmpty()) {
            sql.append("PARTITION BY ");
            sql.append(String.join(", ", window.partitionBy()));
        }
        
        // ORDER BY
        if (!window.orderBy().isEmpty()) {
            if (!window.partitionBy().isEmpty()) {
                sql.append(" ");
            }
            sql.append("ORDER BY ");
            boolean seen = false;
            for (SortOption s : window.orderBy()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(s.field()).append(' ').append(s.order().name());
            }
        }
        
        // Frame clause
        if (window.frameStart() != null && window.frameEnd() != null) {
            sql.append(" ROWS BETWEEN ")
               .append(window.frameStart())
               .append(" AND ")
               .append(window.frameEnd());
        }
        
        sql.append(")");
        
        // Alias
        if (window.alias() != null) {
            sql.append(" AS ").append(window.alias());
        }
        
        return sql.toString();
    }

    private boolean isAggregateWindowFunction(WindowFunctionType type) {
        return type == WindowFunctionType.COUNT ||
               type == WindowFunctionType.SUM ||
               type == WindowFunctionType.AVG ||
               type == WindowFunctionType.MIN ||
               type == WindowFunctionType.MAX;
    }

    /**
     * Parse subquery field (scalar subquery in SELECT clause).
     */
    private String parseSubQueryField(SubQuery.SubQueryFieldDefinition subQueryField) {
        String sql = "(" + subQueryParser.parse(subQueryField.subQuery()) + ")";
        
        if (subQueryField.alias() != null) {
            sql += " AS " + subQueryField.alias();
        }
        
        return sql;
    }

    /**
     * Parse WHERE filters.
     */
    private String parseFilters(List<FilterOption> filters) {
        StringBuilder out = new StringBuilder(filters.size() * 16);
        boolean seen = false;
        for (FilterOption filter : filters) {
            if (seen) {
                out.append(" AND ");
            } else {
                seen = true;
            }
            out.append(parseFilter(filter));
        }
        return out.toString();
    }

    /**
     * Parse HAVING filters (can include aggregate conditions).
     */
    private String parseHavingFilters(List<FilterOption> filters) {
        StringBuilder out = new StringBuilder(filters.size() * 16);
        boolean seen = false;
        for (FilterOption filter : filters) {
            if (seen) {
                out.append(" AND ");
            } else {
                seen = true;
            }
            out.append(parseHavingFilter(filter));
        }
        return out.toString();
    }

    private String parseFilter(FilterOption filter) {
        if (filter instanceof SelectOption(String option, String operator, Object value)) {
            return option + " " + operator + " " + formatValue(value);
        } else if (filter instanceof AggregateFilterOption agg) {
            String aggExpr = buildAggregateExpression(
                new AggregateFieldDefinition(agg.field(), agg.jsonPath(), 
                    agg.aggregationType(), agg.condition(), null)
            );
            return aggExpr + " " + agg.operator() + " " + formatValue(agg.value());
        }
        throw new IllegalArgumentException("Unknown filter type: " + filter.getClass());
    }

    private String parseHavingFilter(FilterOption filter) {
        if (filter instanceof AggregateFilterOption agg) {
            String aggExpr = buildAggregateExpression(
                new AggregateFieldDefinition(agg.field(), agg.jsonPath(),
                    agg.aggregationType(), agg.condition(), null)
            );
            return aggExpr + " " + agg.operator() + " " + formatValue(agg.value());
        }
        return parseFilter(filter);
    }

    private void appendFiltersParameterized(
        StringBuilder out,
        List<FilterOption> filters,
        ArrayList<Object> params,
        boolean having
    ) {
        boolean seen = false;
        for (FilterOption filter : filters) {
            if (seen) {
                out.append(" AND ");
            } else {
                seen = true;
            }
            appendFilterParameterized(out, filter, params, having);
        }
    }

    private void appendFilterParameterized(
        StringBuilder out,
        FilterOption filter,
        ArrayList<Object> params,
        boolean having
    ) {
        if (filter instanceof SelectOption(String option, String operator, Object value)) {
            appendSelectOptionParameterized(out, option, operator, value, params);
            return;
        }

        if (filter instanceof AggregateFilterOption agg) {
            String aggExpr = buildAggregateExpressionParameterized(
                new AggregateFieldDefinition(
                    agg.field(),
                    agg.jsonPath(),
                    agg.aggregationType(),
                    agg.condition(),
                    null
                ),
                params
            );
            appendSelectOptionParameterized(out, aggExpr, agg.operator(), agg.value(), params);
            return;
        }

        throw new IllegalArgumentException("Unknown filter type: " + filter.getClass());
    }

    private void appendSelectOptionParameterized(
        StringBuilder out,
        String lhs,
        String operator,
        Object value,
        ArrayList<Object> params
    ) {
        if (value == null) {
            if ("=".equals(operator) || "==".equals(operator)) {
                out.append(lhs).append(" IS NULL");
                return;
            }
            if ("!=".equals(operator) || "<>".equals(operator)) {
                out.append(lhs).append(" IS NOT NULL");
                return;
            }
            out.append(lhs).append(' ').append(operator).append(" NULL");
            return;
        }

        if ("IN".equalsIgnoreCase(operator) && value instanceof Collection<?> list) {
            out.append(lhs).append(" IN (");
            boolean seen = false;
            for (Object v : list) {
                if (seen) {
                    out.append(", ");
                } else {
                    seen = true;
                }
                out.append('?');
                params.add(v);
            }
            out.append(')');
            return;
        }

        out.append(lhs).append(' ').append(operator).append(' ').append('?');
        params.add(value);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "'" + value + "'";
        }
    }
}