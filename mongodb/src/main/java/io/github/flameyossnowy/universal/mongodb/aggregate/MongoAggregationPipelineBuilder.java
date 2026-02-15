package io.github.flameyossnowy.universal.mongodb.aggregate;

import com.mongodb.client.model.Aggregates;

import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds MongoDB aggregation pipelines from AggregationQuery objects.
 *
 * <p>Translates SQL-style aggregations into MongoDB pipeline stages:
 * - WHERE → $match
 * - GROUP BY → $group
 * - HAVING → $match (after $group)
 * - ORDER BY → $sort
 * - LIMIT → $limit
 *
 * <p>Examples:
 * <pre>{@code
 * // COUNT by status
 * db.users.aggregate([
 *   { $group: { _id: "$status", count: { $sum: 1 } } }
 * ])
 * 
 * // Conditional count
 * db.users.aggregate([
 *   { $group: {
 *       _id: null,
 *       active_count: { $sum: { $cond: [{ $eq: ["$status", "active"] }, 1, 0] } },
 *       inactive_count: { $sum: { $cond: [{ $eq: ["$status", "inactive"] }, 1, 0] } }
 *   }}
 * ])
 * 
 * // With HAVING
 * db.employees.aggregate([
 *   { $group: { _id: "$departmentId", emp_count: { $sum: 1 } } },
 *   { $match: { emp_count: { $gt: 10 } } }
 * ])
 * }</pre>
 */
public class MongoAggregationPipelineBuilder<T, ID> {
    private final RepositoryModel<T, ID> repositoryModel;

    public MongoAggregationPipelineBuilder(RepositoryModel<T, ID> repositoryModel) {
        this.repositoryModel = repositoryModel;
    }

    /**
     * Build MongoDB aggregation pipeline from AggregationQuery.
     */
    public List<Bson> build(AggregationQuery query) {
        List<Bson> pipeline = new ArrayList<>();

        // Stage 1: $match (WHERE clause)
        if (!query.whereFilters().isEmpty()) {
            pipeline.add(Aggregates.match(buildFilter(query.whereFilters())));
        }

        // Stage 2: $group (GROUP BY + aggregations)
        if (!query.groupByFields().isEmpty() || hasAggregations(query.selectFields())) {
            pipeline.add(buildGroupStage(query));
        }

        // Stage 3: $match (HAVING clause)
        if (!query.havingFilters().isEmpty()) {
            pipeline.add(Aggregates.match(buildHavingFilter(query.selectFields(), query.havingFilters())));
        }

        // Stage 4: $project (SELECT clause - rename fields if needed)
        if (needsProjection(query)) {
            pipeline.add(buildProjectStage(query));
        }

        // Stage 5: $sort (ORDER BY)
        if (!query.orderBy().isEmpty()) {
            pipeline.add(buildSortStage(query.orderBy()));
        }

        // Stage 6: $limit
        if (query.limit() > 0) {
            pipeline.add(Aggregates.limit(query.limit()));
        }

        return pipeline;
    }

    /**
     * Build $group stage with aggregation expressions.
     */
    private Bson buildGroupStage(AggregationQuery query) {
        Document groupId = extractGroupId(query);

        // Add aggregation accumulators
        for (FieldDefinition field : query.selectFields()) {
            if (field instanceof AggregateFieldDefinition agg) {
                String accumulator = buildAccumulator(agg);
                groupId.put(agg.alias(), Document.parse(accumulator));
            } else if (field instanceof SimpleFieldDefinition simple) {
                // Non-aggregated fields in SELECT must be in GROUP BY
                if (!query.groupByFields().contains(simple.getFieldName())) {
                    // Include first value
                    groupId.put(simple.getFieldName(), new Document("$first", "$" + simple.getFieldName()));
                }
            }
        }

        return new Document("$group", groupId);
    }

    private static @NotNull Document extractGroupId(AggregationQuery query) {
        Document groupId;

        if (query.groupByFields().isEmpty()) {
            // No GROUP BY means aggregate everything into single result
            groupId = new Document("_id", null);
        } else if (query.groupByFields().size() == 1) {
            // Single field GROUP BY
            groupId = new Document("_id", "$" + query.groupByFields().getFirst());
        } else {
            // Multiple field GROUP BY
            Document compound = new Document();
            for (String field : query.groupByFields()) {
                compound.put(field, "$" + field);
            }
            groupId = new Document("_id", compound);
        }
        return groupId;
    }

    /**
     * Build MongoDB accumulator expression for aggregation.
     */
    private String buildAccumulator(AggregateFieldDefinition agg) {
        String field = "$" + agg.field();
        
        switch (agg.aggregationType()) {
            case COUNT:
                return "{ $sum: 1 }";
                
            case COUNT_DISTINCT:
                return "{ $addToSet: " + field + " }"; // Will need $size in projection
                
            case COUNT_IF:
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("COUNT_IF requires a condition");
                }
                String condition = buildConditionExpression(agg.field(), agg.condition());
                return "{ $sum: { $cond: [" + condition + ", 1, 0] } }";
                
            case SUM:
                return "{ $sum: " + field + " }";
                
            case SUM_IF:
                if (agg.condition() == null) {
                    throw new IllegalArgumentException("SUM_IF requires a condition");
                }
                String sumCondition = buildConditionExpression(agg.field(), agg.condition());
                return "{ $sum: { $cond: [" + sumCondition + ", " + field + ", 0] } }";
                
            case AVG:
                return "{ $avg: " + field + " }";
                
            case MIN:
                return "{ $min: " + field + " }";
                
            case MAX:
                return "{ $max: " + field + " }";
                
            case STRING_AGG:
                // Not a standard MongoDB operator, but can use $push then $reduce
                return "{ $push: " + field + " }"; // Will need processing in $project
                
            case ARRAY_LENGTH:
                return "{ $size: { $ifNull: [" + field + ", []] } }";
                
            case JSON_ARRAY_AGG:
                return "{ $push: " + field + " }";
                
            case JSON_OBJECT_AGG:
                String valueField = agg.condition() instanceof SelectOption sel ?
                    String.valueOf(sel.value()) : "value";
                return "{ $push: { k: " + field + ", v: $" + valueField + " } }";
                
            case STDDEV:
                return "{ $stdDevPop: " + field + " }";
                
            case VARIANCE:
                // MongoDB doesn't have built-in variance, calculate from stddev
                return "{ $pow: [{ $stdDevPop: " + field + " }, 2] }";
                
            case FIRST:
                return "{ $first: " + field + " }";
                
            case LAST:
                return "{ $last: " + field + " }";
                
            default:
                throw new IllegalArgumentException("Unsupported aggregation type: " + agg.aggregationType());
        }
    }

    /**
     * Build condition expression for $cond operator.
     */
    private String buildConditionExpression(String field, FilterOption condition) {
        if (condition instanceof SelectOption select) {
            String operator = select.operator();
            Object value = select.value();
            
            String mongoOperator = switch (operator.toUpperCase()) {
                case "=", "EQ" -> "$eq";
                case "!=", "NE" -> "$ne";
                case ">", "GT" -> "$gt";
                case ">=", "GTE" -> "$gte";
                case "<", "LT" -> "$lt";
                case "<=", "LTE" -> "$lte";
                default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
            };
            
            String valueStr = formatValue(value);
            return "{ " + mongoOperator + ": [\"$" + field + "\", " + valueStr + "] }";
        }
        
        throw new IllegalArgumentException("Unsupported condition type: " + condition.getClass());
    }

    /**
     * Build $project stage to rename fields and handle post-aggregation operations.
     */
    private Bson buildProjectStage(AggregationQuery query) {
        Document projection = new Document();
        
        // Include _id if it's a grouped field
        if (!query.groupByFields().isEmpty()) {
            if (query.groupByFields().size() == 1) {
                String groupField = query.groupByFields().getFirst();
                projection.put(groupField, "$_id");
            } else {
                for (String field : query.groupByFields()) {
                    projection.put(field, "$_id." + field);
                }
            }
            projection.put("_id", 0); // Exclude _id field
        }
        
        // Include aggregated fields
        for (FieldDefinition field : query.selectFields()) {
            if (field instanceof AggregateFieldDefinition agg) {
                if (agg.aggregationType() == AggregationType.COUNT_DISTINCT) {
                    // COUNT(DISTINCT) needs $size of $addToSet result
                    projection.put(agg.alias(), new Document("$size", "$" + agg.alias()));
                } else {
                    projection.put(agg.alias(), "$" + agg.alias());
                }
            }
        }
        
        return new Document("$project", projection);
    }

    /**
     * Build $sort stage.
     */
    private Bson buildSortStage(List<SortOption> sortOptions) {
        Document sort = new Document();
        for (SortOption option : sortOptions) {
            sort.put(option.field(), option.order() == SortOrder.ASCENDING ? 1 : -1);
        }
        return new Document("$sort", sort);
    }

    /**
     * Build $match filter for WHERE clause.
     */
    private Bson buildFilter(List<FilterOption> filters) {
        Document filter = new Document();
        
        for (FilterOption option : filters) {
            if (option instanceof SelectOption(String option1, String operator, Object value)) {

                switch (operator.toUpperCase()) {
                    case "=", "EQ":
                        filter.put(option1, value);
                        break;
                    case "!=", "NE":
                        filter.put(option1, new Document("$ne", value));
                        break;
                    case ">", "GT":
                        filter.put(option1, new Document("$gt", value));
                        break;
                    case ">=", "GTE":
                        filter.put(option1, new Document("$gte", value));
                        break;
                    case "<", "LT":
                        filter.put(option1, new Document("$lt", value));
                        break;
                    case "<=", "LTE":
                        filter.put(option1, new Document("$lte", value));
                        break;
                    case "IN":
                        filter.put(option1, new Document("$in", value));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported operator: " + operator);
                }
            }
        }
        
        return filter;
    }

    private @NotNull String resolveAggregateOutputField(
        @NotNull List<FieldDefinition> selectFields,
        @NotNull AggregateFilterOption having
    ) {
        for (FieldDefinition fd : selectFields) {
            if (!(fd instanceof AggregateFieldDefinition a)) {
                continue;
            }

            if (!a.field().equals(having.field())) {
                continue;
            }
            if (a.aggregationType() != having.aggregationType()) {
                continue;
            }
            if (!java.util.Objects.equals(a.jsonPath(), having.jsonPath())) {
                continue;
            }
            if (!java.util.Objects.equals(a.condition(), having.condition())) {
                continue;
            }

            return a.alias();
        }

        // Fallback to field name (legacy behaviour) if not found; may still fail,
        // but preserves previous semantics.
        return having.field();
    }

    /**
     * Build $match filter for HAVING clause (after $group).
     */
    private Bson buildHavingFilter(List<FieldDefinition> selectFields, List<FilterOption> filters) {
        Document filter = new Document();
        
        for (FilterOption option : filters) {
            if (option instanceof AggregateFilterOption agg) {
                // HAVING clause references aggregated output fields (typically aliases).
                // If no alias was provided in the filter, try to resolve it from the SELECT list.
                String field;
                if (agg.alias() != null && !agg.alias().isBlank()) {
                    field = agg.alias();
                } else {
                    field = resolveAggregateOutputField(selectFields, agg);
                }
                Object value = agg.value();
                
                switch (agg.operator().toUpperCase()) {
                    case "=", "EQ":
                        filter.put(field, value);
                        break;
                    case ">", "GT":
                        filter.put(field, new Document("$gt", value));
                        break;
                    case ">=", "GTE":
                        filter.put(field, new Document("$gte", value));
                        break;
                    case "<", "LT":
                        filter.put(field, new Document("$lt", value));
                        break;
                    case "<=", "LTE":
                        filter.put(field, new Document("$lte", value));
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported operator: " + agg.operator());
                }
            }
        }
        
        return filter;
    }

    private boolean hasAggregations(List<FieldDefinition> fields) {
        for (FieldDefinition field : fields) {
            if (field instanceof AggregateFieldDefinition) {
                return true;
            }
        }
        return false;
    }

    private boolean needsProjection(AggregationQuery query) {
        return !query.groupByFields().isEmpty() || hasAggregations(query.selectFields());
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        } else {
            return value.toString();
        }
    }
}