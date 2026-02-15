package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;

import java.util.List;
import java.util.StringJoiner;

/**
 * Parses subqueries into SQL.
 * Handles correlated subqueries, derived tables, and scalar subqueries.
 */
public class SubQueryParser<T, ID> {
    private final RepositoryModel<T, ID> parentRepositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final QueryParseEngine.SQLType dialect;

    public SubQueryParser(
            RepositoryModel<T, ID> parentRepositoryModel,
            TypeResolverRegistry resolverRegistry,
            QueryParseEngine.SQLType dialect) {
        this.parentRepositoryModel = parentRepositoryModel;
        this.resolverRegistry = resolverRegistry;
        this.dialect = dialect;
    }

    /**
     * Parse a subquery into SQL.
     * Examples:
     * - SELECT id FROM users WHERE active = true
     * - SELECT COUNT(*) FROM tasks WHERE employee_id = e.id
     * - SELECT department_id, COUNT(*) FROM employees GROUP BY department_id HAVING COUNT(*) > 10
     */
    public String parse(SubQuery subQuery) {
        StringBuilder sql = new StringBuilder("SELECT ");

        // Get repository model for the subquery entity
        RepositoryModel<?, ?> subQueryModel = GeneratedMetadata.getByEntityClass(subQuery.entityClass());
        if (subQueryModel == null) {
            throw new IllegalArgumentException("No repository model found for " + subQuery.entityClass());
        }

        // SELECT clause
        if (subQuery.selectFields().isEmpty()) {
            sql.append("*");
        } else {
            AggregationQueryParser<?, ?> aggParser = new AggregationQueryParser<>(
                subQueryModel, resolverRegistry, dialect
            );
            StringJoiner joiner = new StringJoiner(", ");
            for (FieldDefinition field : subQuery.selectFields()) {
                String s = parseFieldDefinition(field, aggParser);
                joiner.add(s);
            }
            sql.append(joiner);
        }

        // FROM clause
        if (subQuery.fromSubQuery() != null) {
            // Derived table: FROM (subquery) AS alias
            sql.append(" FROM (");
            sql.append(parse(subQuery.fromSubQuery()));
            sql.append(") AS ").append(subQuery.fromSubQuery().alias());
        } else {
            sql.append(" FROM ").append(subQueryModel.tableName());
        }

        // WHERE clause
        if (!subQuery.whereFilters().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(parseFilters(subQuery.whereFilters(), subQueryModel));
        }

        // GROUP BY clause
        if (!subQuery.groupByFields().isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(String.join(", ", subQuery.groupByFields()));
        }

        // HAVING clause
        if (!subQuery.havingFilters().isEmpty()) {
            sql.append(" HAVING ");
            AggregationQueryParser<?, ?> aggParser = new AggregationQueryParser<>(
                subQueryModel, resolverRegistry, dialect
            );
            sql.append(parseHavingFilters(subQuery.havingFilters(), aggParser));
        }

        // ORDER BY clause
        if (!subQuery.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner joiner = new StringJoiner(", ");
            for (SortOption s : subQuery.orderBy()) {
                String string = s.field() + " " + s.order().name();
                joiner.add(string);
            }
            sql.append(joiner);
        }

        // LIMIT clause
        if (subQuery.limit() > 0) {
            sql.append(" LIMIT ").append(subQuery.limit());
        }

        return sql.toString();
    }

    private String parseFieldDefinition(FieldDefinition field, AggregationQueryParser<?, ?> aggParser) {
        if (field instanceof SimpleFieldDefinition simple) {
            String sql = simple.getFieldName();
            if (simple.alias() != null && !simple.alias().equals(simple.getFieldName())) {
                sql += " AS " + simple.alias();
            }
            return sql;
        } else if (field instanceof AggregateFieldDefinition agg) {
            // Delegate to aggregation parser
            return buildAggregateExpression(agg) + 
                   (agg.alias() != null ? " AS " + agg.alias() : "");
        } else if (field instanceof SubQuery.SubQueryFieldDefinition(SubQuery subQuery, String alias)) {
            // Nested subquery
            return "(" + parse(subQuery) + ")" +
                   (alias != null ? " AS " + alias : "");
        }
        throw new IllegalArgumentException("Unknown field definition type: " + field.getClass());
    }

    private String buildAggregateExpression(AggregateFieldDefinition agg) {
        String field = agg.field();

        return switch (agg.aggregationType()) {
            case COUNT -> "COUNT(*)";
            case COUNT_DISTINCT -> "COUNT(DISTINCT " + field + ")";
            case COUNT_IF -> {
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("COUNT_IF requires a condition");
                }
                yield "COUNT(CASE WHEN " + parseCondition(field, agg.condition()) + " THEN 1 END)";
            }
            case SUM -> "SUM(" + field + ")";
            case AVG -> "AVG(" + field + ")";
            case MIN -> "MIN(" + field + ")";
            case MAX -> "MAX(" + field + ")";
            default -> throw new IllegalArgumentException("Unsupported aggregation: " + agg.aggregationType());
        };
    }

    private String parseCondition(String field, FilterOption condition) {
        if (condition instanceof SelectOption select) {
            return field + " " + select.operator() + " " + formatValue(select.value());
        }
        throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass());
    }

    private String parseFilters(List<FilterOption> filters, RepositoryModel<?, ?> model) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (FilterOption filter : filters) {
            String s = parseFilter(filter, model);
            joiner.add(s);
        }
        return joiner.toString();
    }

    private String parseFilter(FilterOption filter, RepositoryModel<?, ?> model) {
        if (filter instanceof SelectOption(String field, String operator, Object value)) {

            // Handle outer field references (correlated subqueries)
            // If the value is an OuterFieldReference, prefix with parent table alias
            if (value instanceof SubQuery.OuterFieldReference(String fieldName)) {
                // This is a correlated subquery: WHERE t.field = parent.field
                String parentTableAlias = getParentTableAlias();
                return field + " " + operator + " " + parentTableAlias + "." + fieldName;
            }
            
            return field + " " + operator + " " + formatValue(value);
            
        } else if (filter instanceof AggregateFilterOption agg) {
            String aggExpr = buildAggregateExpression(
                new AggregateFieldDefinition(agg.field(), agg.jsonPath(),
                    agg.aggregationType(), agg.condition(), null)
            );
            return aggExpr + " " + agg.operator() + " " + formatValue(agg.value());
        }
        
        throw new IllegalArgumentException("Unknown filter type: " + filter.getClass());
    }

    private String parseHavingFilters(List<FilterOption> filters, AggregationQueryParser<?, ?> aggParser) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (FilterOption filter : filters) {
            String s = parseHavingFilter(filter, aggParser);
            joiner.add(s);
        }
        return joiner.toString();
    }

    private String parseHavingFilter(FilterOption filter, AggregationQueryParser<?, ?> aggParser) {
        if (filter instanceof AggregateFilterOption agg) {
            String aggExpr = buildAggregateExpression(
                new AggregateFieldDefinition(agg.field(), agg.jsonPath(),
                    agg.aggregationType(), agg.condition(), null)
            );
            return aggExpr + " " + agg.operator() + " " + formatValue(agg.value());
        }
        return parseFilter(filter, null);
    }

    /**
     * Get the alias for the parent table (for correlated subqueries).
     * Typically the first letter of the table name (e.g., 'e' for employees).
     */
    private String getParentTableAlias() {
        String tableName = parentRepositoryModel.tableName();
        return tableName.substring(0, 1).toLowerCase();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof SubQuery subQuery) {
            return "(" + parse(subQuery) + ")";
        } else {
            return "'" + value + "'";
        }
    }
}