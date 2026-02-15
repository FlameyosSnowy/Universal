package io.github.flameyossnowy.universal.mongodb.aggregate;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

/**
 * MongoDB implementation of aggregation and complex query methods.
 * This is added to MongoRepositoryAdapter.
 */
public class MongoAggregationImplementation<T, ID> {
    
    private final MongoCollection<Document> collection;
    private final MongoAggregationPipelineBuilder<T, ID> pipelineBuilder;
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry typeResolverRegistry;

    public MongoAggregationImplementation(
            MongoCollection<Document> collection,
            RepositoryModel<T, ID> repositoryModel,
            TypeResolverRegistry typeResolverRegistry) {
        this.collection = collection;
        this.repositoryModel = repositoryModel;
        this.typeResolverRegistry = typeResolverRegistry;
        this.pipelineBuilder = new MongoAggregationPipelineBuilder<>(repositoryModel);
    }

    /**
     * Execute aggregation query and return raw results.
     */
    public List<Map<String, Object>> aggregate(@NotNull AggregationQuery query) {
        List<Bson> pipeline = pipelineBuilder.build(query);
        
        AggregateIterable<Document> results = collection.aggregate(pipeline);
        
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (MongoCursor<Document> cursor = results.iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                resultList.add(documentToMap(doc));
            }
        }
        
        return resultList;
    }

    /**
     * Execute aggregation query and map each document to an entity using a caller-provided mapper.
     *
     * <p>This overload exists so repository adapters can map documents using generated ObjectModel code
     * (or any other strategy) instead of reflection.</p>
     */
    public List<T> aggregateEntities(@NotNull AggregationQuery query, @NotNull Function<Document, T> documentMapper) {
        List<Bson> pipeline = pipelineBuilder.build(query);
        AggregateIterable<Document> results = collection.aggregate(pipeline);

        List<T> out = new ArrayList<>();
        try (MongoCursor<Document> cursor = results.iterator()) {
            while (cursor.hasNext()) {
                out.add(documentMapper.apply(cursor.next()));
            }
        }

        return out;
    }

    /**
     * Execute window function query.
     * 
     * MongoDB 5.0+ supports $setWindowFields for window functions.
     */
    public List<Map<String, Object>> window(@NotNull WindowQuery query) {
        List<Bson> pipeline = buildWindowPipeline(query);
        
        AggregateIterable<Document> results = collection.aggregate(pipeline);
        
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (MongoCursor<Document> cursor = results.iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                resultList.add(documentToMap(doc));
            }
        }
        
        return resultList;
    }

    /**
     * Execute window function query and map to result type.
     */
    public <R> List<R> window(@NotNull WindowQuery query, @NotNull Class<R> resultType) {
        if (resultType == repositoryModel.getEntityClass()) {
            throw new UnsupportedOperationException(
                "Entity window queries must be performed via windowEntities(query) on the repository adapter so it can use generated ObjectModel/ValueReader mapping."
            );
        }
        throw new UnsupportedOperationException(
            "Typed window projections are not supported. Use window(query) for Map results, or windowEntities(query) for entities."
        );
    }

    /**
     * Execute window query and map each document to an entity using a caller-provided mapper.
     */
    public List<T> windowEntities(@NotNull WindowQuery query, @NotNull Function<Document, T> documentMapper) {
        List<Bson> pipeline = buildWindowPipeline(query);
        AggregateIterable<Document> results = collection.aggregate(pipeline);

        List<T> out = new ArrayList<>();
        try (MongoCursor<Document> cursor = results.iterator()) {
            while (cursor.hasNext()) {
                out.add(documentMapper.apply(cursor.next()));
            }
        }

        return out;
    }

    /**
     * Execute raw aggregation pipeline.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> executeAggregation(@NotNull Object rawQuery) {
        List<Bson> pipeline;
        
        if (rawQuery instanceof List<?> list) {
            pipeline = (List<Bson>) list;
        } else {
            throw new IllegalArgumentException("MongoDB aggregation requires List<Bson> pipeline");
        }
        
        AggregateIterable<Document> results = collection.aggregate(pipeline);
        
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (MongoCursor<Document> cursor = results.iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                resultList.add(documentToMap(doc));
            }
        }
        
        return resultList;
    }

    /**
     * Get scalar value from aggregation.
     */
    public <R> R aggregateScalar(
            @NotNull AggregationQuery query,
            @NotNull String fieldName,
            @NotNull Class<R> type) {
        List<Map<String, Object>> results = aggregate(query);
        
        if (results.isEmpty()) {
            return null;
        }
        
        Object value = results.getFirst().get(fieldName);
        if (value == null) {
            return null;
        }
        
        return castValue(value, type);
    }

    /**
     * Build MongoDB $setWindowFields pipeline for window functions.
     */
    private List<Bson> buildWindowPipeline(WindowQuery query) {
        List<Bson> pipeline = new ArrayList<>();
        
        // Add $match stage if there are WHERE filters
        if (!query.whereFilters().isEmpty()) {
            Document filter = buildFilter(query.whereFilters());
            pipeline.add(new Document("$match", filter));
        }
        
        // Build $setWindowFields stage
        Document windowStage = new Document();
        Document output = new Document();
        
        for (FieldDefinition field : query.selectFields()) {
            if (field instanceof WindowFieldDefinition window) {
                Document windowDef = buildWindowDefinition(window);
                output.put(window.alias(), windowDef);
            }
        }
        
        if (!output.isEmpty()) {
            windowStage.put("output", output);
            
            // Add partitionBy if any window has it
            for (FieldDefinition field : query.selectFields()) {
                if (field instanceof WindowFieldDefinition window) {
                    if (!window.partitionBy().isEmpty()) {
                        if (window.partitionBy().size() == 1) {
                            windowStage.put("partitionBy", "$" + window.partitionBy().getFirst());
                        } else {
                            Document partitionDoc = new Document();
                            for (String partField : window.partitionBy()) {
                                partitionDoc.put(partField, "$" + partField);
                            }
                            windowStage.put("partitionBy", partitionDoc);
                        }
                        break; // Use first partition definition
                    }
                }
            }
            
            // Add sortBy if any window has it
            for (FieldDefinition field : query.selectFields()) {
                if (field instanceof WindowFieldDefinition window) {
                    if (!window.orderBy().isEmpty()) {
                        Document sortDoc = new Document();
                        for (SortOption sort : window.orderBy()) {
                            sortDoc.put(sort.field(), sort.order() == SortOrder.ASCENDING ? 1 : -1);
                        }
                        windowStage.put("sortBy", sortDoc);
                        break; // Use first sort definition
                    }
                }
            }
            
            pipeline.add(new Document("$setWindowFields", windowStage));
        }
        
        // Add $project stage to include non-window fields
        Document project = new Document();
        for (FieldDefinition field : query.selectFields()) {
            if (field instanceof SimpleFieldDefinition simple) {
                project.put(simple.getFieldName(), 1);
            } else if (field instanceof WindowFieldDefinition window) {
                project.put(window.alias(), 1);
            }
        }
        if (!project.isEmpty()) {
            pipeline.add(new Document("$project", project));
        }
        
        // Add $sort if needed
        if (!query.orderBy().isEmpty()) {
            Document sort = new Document();
            query.orderBy().forEach(s -> 
                sort.put(s.field(), s.order() == SortOrder.ASCENDING ? 1 : -1)
            );
            pipeline.add(new Document("$sort", sort));
        }
        
        // Add $limit if needed
        if (query.limit() > 0) {
            pipeline.add(new Document("$limit", query.limit()));
        }
        
        return pipeline;
    }

    /**
     * Build window function definition for MongoDB.
     */
    private Document buildWindowDefinition(WindowFieldDefinition window) {
        Document def = new Document();
        
        String mongoOp = switch (window.functionType()) {
            case ROW_NUMBER -> "$rowNumber";
            case RANK -> "$rank";
            case DENSE_RANK -> "$denseRank";
            case COUNT, SUM -> "$sum";
            case AVG -> "$avg";
            case MIN -> "$min";
            case MAX -> "$max";
            default -> throw new IllegalArgumentException("Unsupported window function: " + window.functionType());
        };
        
        if (window.functionType() == WindowFunctionType.COUNT) {
            def.put(mongoOp, 1);
        } else if (window.functionType() == WindowFunctionType.ROW_NUMBER ||
                   window.functionType() == WindowFunctionType.RANK ||
                   window.functionType() == WindowFunctionType.DENSE_RANK) {
            def.put(mongoOp, new Document());
        } else {
            def.put(mongoOp, "$" + window.field());
        }
        
        // Add window specification
        if (window.frameStart() != null && window.frameEnd() != null) {
            Document windowSpec = new Document();
            windowSpec.put("documents", Arrays.asList(window.frameStart(), window.frameEnd()));
            def.put("window", windowSpec);
        }
        
        return def;
    }

    /**
     * Build filter document from filter options.
     */
    private Document buildFilter(List<FilterOption> filters) {
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

    /**
     * Convert MongoDB Document to Map.
     */
    private Map<String, Object> documentToMap(Document doc) {
        return new LinkedHashMap<>(doc);
    }

    /**
     * Cast value to target type with proper conversions.
     */
    @SuppressWarnings("unchecked")
    private <R> R castValue(Object value, Class<R> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isInstance(value)) {
            return (R) value;
        }
        
        // Handle numeric conversions
        if (value instanceof Number num) {
            if (targetType == Long.class || targetType == long.class) {
                return (R) Long.valueOf(num.longValue());
            }
            if (targetType == Integer.class || targetType == int.class) {
                return (R) Integer.valueOf(num.intValue());
            }
            if (targetType == Double.class || targetType == double.class) {
                return (R) Double.valueOf(num.doubleValue());
            }
            if (targetType == Float.class || targetType == float.class) {
                return (R) Float.valueOf(num.floatValue());
            }
            if (targetType == Short.class || targetType == short.class) {
                return (R) Short.valueOf(num.shortValue());
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return (R) Byte.valueOf(num.byteValue());
            }
        }
        
        // Handle string conversions
        if (targetType == String.class) {
            return (R) value.toString();
        }
        
        // Default: try direct cast
        return targetType.cast(value);
    }
}