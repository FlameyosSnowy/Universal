package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Enhanced field-scoped operator builder with support for aggregations,
 * subqueries, and advanced filtering.
 *
 * <p>Ensures operators are attached to a concrete field and prevents
 * malformed filter construction.</p>
 *
 * <p>Examples:
 * <pre>{@code
 * // Basic filtering
 * query.where("age").gte(18)
 *
 * // Aggregation
 * query.select(field("status").count().as("status_count"))
 *
 * // Conditional count
 * query.select(field("status").countIf(eq("active")).as("active_count"))
 *
 * // Subquery
 * query.where("id").in(
 *     subQuery(User.class)
 *         .select("userId")
 *         .where("active").eq(true)
 * )
 *
 * // JSON operations
 * query.whereJson("metadata", "$.tags").arrayLength().gte(3)
 * }</pre>
 */
@SuppressWarnings("unused")
public class QueryField<B extends Filterable> implements FieldDefinition {
    private final B builder;
    private final String field;
    private final String jsonPath;
    private final AggregationType aggregationType;
    private final FilterOption condition; // For conditional aggregations
    private final String alias;

    @Contract(pure = true)
    QueryField(B builder, String field) {
        this(builder, field, null, null, null, null);
    }

    @Contract(pure = true)
    QueryField(B builder, String field, String jsonPath) {
        this(builder, field, jsonPath, null, null, null);
    }

    @Contract(pure = true)
    private QueryField(B builder, String field, String jsonPath,
                       AggregationType aggregationType, FilterOption condition, String alias) {
        this.builder = builder;
        this.field = field;
        this.jsonPath = jsonPath;
        this.aggregationType = aggregationType;
        this.condition = condition;
        this.alias = alias;
    }

    // ==================== Basic Comparison Operators ====================

    private B add(String operator, Object value) {
        FilterOption filter = createFilter(operator, value);
        builder.addFilter(filter);
        return builder;
    }

    private FilterOption createFilter(String operator, Object value) {
        if (aggregationType != null) {
            return new AggregateFilterOption(field, jsonPath, operator, value, aggregationType, condition, alias);
        }
        if (jsonPath != null) {
            return new JsonSelectOption(field, jsonPath, operator, value);
        }
        return new SelectOption(field, operator, value);
    }

    public B eq(Object value) { return add("=", value); }
    public B ne(Object value) { return add("!=", value); }
    public B gt(Object value) { return add(">", value); }
    public B gte(Object value) { return add(">=", value); }
    public B lt(Object value) { return add("<", value); }
    public B lte(Object value) { return add("<=", value); }
    public B in(Collection<?> values) { return add("IN", values); }
    public B notIn(Collection<?> values) { return add("NOT IN", values); }
    public B like(String pattern) { return add("LIKE", pattern); }
    public B isNull() { return add("IS NULL", null); }
    public B isNotNull() { return add("IS NOT NULL", null); }
    public B between(Object start, Object end) { return add("BETWEEN", new Object[]{start, end}); }

    // ==================== Logical Operators ====================

    public B not() { return add("NOT", null); }
    public B and() { return add("AND", null); }
    public B or() { return add("OR", null); }

    // ==================== Subquery Support ====================

    /**
     * Filter by a subquery result.
     *
     * <pre>{@code
     * query.where("departmentId").in(
     *     subQuery(Department.class)
     *         .select("id")
     *         .where("active").eq(true)
     * )
     * }</pre>
     */
    public B in(SubQuery subQuery) {
        return add("IN", subQuery);
    }

    public B notIn(SubQuery subQuery) {
        return add("NOT IN", subQuery);
    }

    public B exists(SubQuery subQuery) {
        return add("EXISTS", subQuery);
    }

    public B notExists(SubQuery subQuery) {
        return add("NOT EXISTS", subQuery);
    }

    // ==================== Aggregation Functions ====================

    /**
     * Apply COUNT aggregation to this field.
     *
     * <pre>{@code
     * query.select(field("id").count().as("total_count"))
     * }</pre>
     */
    public QueryField<B> count() {
        return withAggregation(AggregationType.COUNT);
    }

    /**
     * Apply COUNT DISTINCT aggregation.
     *
     * <pre>{@code
     * query.select(field("role").countDistinct().as("unique_roles"))
     * }</pre>
     */
    public QueryField<B> countDistinct() {
        return withAggregation(AggregationType.COUNT_DISTINCT);
    }

    /**
     * Conditional count using CASE WHEN.
     *
     * <pre>{@code
     * query.select(
     *     field("status").countIf(eq("active")).as("active_count"),
     *     field("status").countIf(eq("inactive")).as("inactive_count")
     * )
     * }</pre>
     */
    public QueryField<B> countIf(FilterOption condition) {
        return new QueryField<>(builder, field, jsonPath, AggregationType.COUNT_IF, condition, alias);
    }

    public QueryField<B> sum() {
        return withAggregation(AggregationType.SUM);
    }

    public QueryField<B> avg() {
        return withAggregation(AggregationType.AVG);
    }

    public QueryField<B> min() {
        return withAggregation(AggregationType.MIN);
    }

    public QueryField<B> max() {
        return withAggregation(AggregationType.MAX);
    }

    /**
     * Apply SUM with a condition.
     *
     * <pre>{@code
     * query.select(field("amount").sumIf(where("status").eq("completed")).as("completed_total"))
     * }</pre>
     */
    public QueryField<B> sumIf(FilterOption condition) {
        return new QueryField<>(builder, field, jsonPath, AggregationType.SUM_IF, condition, alias);
    }

    // ==================== String Aggregations ====================

    /**
     * Concatenate string values (GROUP_CONCAT in MySQL, STRING_AGG in PostgreSQL).
     */
    public QueryField<B> stringAgg(String delimiter) {
        return new QueryField<>(builder, field, jsonPath, AggregationType.STRING_AGG,
            new SelectOption("delimiter", "=", delimiter), alias);
    }

    // ==================== Array/JSON Aggregations ====================

    /**
     * Get array length for JSON fields.
     *
     * <pre>{@code
     * query.where("tasks").arrayLength().gt(5)
     * query.select(field("tasks").arrayLength().as("task_count"))
     * }</pre>
     */
    public QueryField<B> arrayLength() {
        if (jsonPath == null && !field.contains(".")) {
            // Top-level JSON field
            return new QueryField<>(builder, field, null, AggregationType.ARRAY_LENGTH, null, alias);
        }
        return withAggregation(AggregationType.ARRAY_LENGTH);
    }

    /**
     * Aggregate into JSON array.
     */
    public QueryField<B> jsonArrayAgg() {
        return withAggregation(AggregationType.JSON_ARRAY_AGG);
    }

    /**
     * Aggregate into JSON object.
     */
    public QueryField<B> jsonObjectAgg(String valueField) {
        return new QueryField<>(builder, field, jsonPath, AggregationType.JSON_OBJECT_AGG,
            new SelectOption("valueField", "=", valueField), alias);
    }

    // ==================== Window Functions ====================

    /**
     * Apply ROW_NUMBER window function.
     *
     * <pre>{@code
     * query.select(field("id").rowNumber()
     *     .partitionBy("departmentId")
     *     .orderBy("salary", DESC)
     *     .as("rank_in_dept"))
     * }</pre>
     */
    public WindowFunctionBuilder<B> rowNumber() {
        return new WindowFunctionBuilder<>(builder, field, WindowFunctionType.ROW_NUMBER);
    }

    public WindowFunctionBuilder<B> rank() {
        return new WindowFunctionBuilder<>(builder, field, WindowFunctionType.RANK);
    }

    public WindowFunctionBuilder<B> denseRank() {
        return new WindowFunctionBuilder<>(builder, field, WindowFunctionType.DENSE_RANK);
    }

    /**
     * Cumulative count with window function.
     */
    public WindowFunctionBuilder<B> cumulativeCount() {
        return new WindowFunctionBuilder<>(builder, field, WindowFunctionType.COUNT);
    }

    public WindowFunctionBuilder<B> cumulativeSum() {
        return new WindowFunctionBuilder<>(builder, field, WindowFunctionType.SUM);
    }

    public WindowFunctionBuilder<B> cumulativeAvg() {
        return new WindowFunctionBuilder<>(builder, field, WindowFunctionType.AVG);
    }

    // ==================== Alias Support ====================

    /**
     * Assign an alias to this field or aggregation.
     *
     * <pre>{@code
     * query.select(field("id").count().as("total_users"))
     * }</pre>
     */
    public AggregateFieldDefinition as(String alias) {
        return new AggregateFieldDefinition(field, jsonPath, aggregationType, condition, alias);
    }

    // ==================== Helper Methods ====================

    private QueryField<B> withAggregation(AggregationType type) {
        return new QueryField<>(builder, field, jsonPath, type, null, alias);
    }

    // ==================== Getters ====================

    public String getField() {
        return field;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public AggregationType getAggregationType() {
        return aggregationType;
    }

    public FilterOption getCondition() {
        return condition;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public String getFieldName() {
        return field;
    }
}