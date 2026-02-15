package io.github.flameyossnowy.universal.mongodb.aggregate;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import io.github.flameyossnowy.universal.api.options.AggregationQuery;
import io.github.flameyossnowy.universal.api.options.WindowQuery;
import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extension to MongoRepositoryAdapter with aggregation support.
 * This would be mixed into the existing MongoRepositoryAdapter class.
 */
public class MongoAggregationSupport<T, ID> {
    
    private final MongoRepositoryAdapter<T, ID> adapter;
    private final MongoAggregationPipelineBuilder<T, ID> pipelineBuilder;

    public MongoAggregationSupport(MongoRepositoryAdapter<T, ID> adapter) {
        this.adapter = adapter;
        this.pipelineBuilder = new MongoAggregationPipelineBuilder<>(adapter.getRepositoryModel());
    }

    /**
     * Execute aggregation query.
     */
    public List<Map<String, Object>> aggregate(@NotNull AggregationQuery query) {
        List<Bson> pipeline = pipelineBuilder.build(query);

        return aggregate(pipeline);
    }

    @NotNull
    private List<Map<String, Object>> aggregate(List<Bson> pipeline) {
        AggregateIterable<Document> results = adapter.getCollection().aggregate(pipeline);

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
     * Execute aggregation and map to result type.
     */
    public <R> List<R> aggregate(@NotNull AggregationQuery query, @NotNull Class<R> resultType) {
        throw new UnsupportedOperationException(
            "Typed aggregation projections are not supported. Use aggregate(query) for Map results, or aggregateEntities(query) on the repository adapter for entities."
        );
    }

    /**
     * Execute window function query.
     * 
     * Note: MongoDB 5.0+ supports $setWindowFields for window functions.
     */
    public List<Map<String, Object>> window(@NotNull WindowQuery query) {
        // MongoDB window functions use $setWindowFields
        List<Bson> pipeline = buildWindowPipeline(query);

        return aggregate(pipeline);
    }

    public <R> List<R> window(@NotNull WindowQuery query, @NotNull Class<R> resultType) {
        throw new UnsupportedOperationException(
            "Typed window projections are not supported. Use window(query) for Map results, or windowEntities(query) on the repository adapter for entities."
        );
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

        return aggregate(pipeline);
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
        
        Object value = results.get(0).get(fieldName);
        return type.cast(value);
    }

    /**
     * Build MongoDB $setWindowFields pipeline for window functions.
     */
    private List<Bson> buildWindowPipeline(WindowQuery query) {
        // This is a simplified implementation
        // Full implementation would parse WindowFieldDefinitions
        List<Bson> pipeline = new ArrayList<>();
        
        // Add $match stage if there are WHERE filters
        if (!query.whereFilters().isEmpty()) {
            Document filter = new Document();
            // Build filter from whereFilters
            pipeline.add(new Document("$match", filter));
        }
        
        // Add $setWindowFields stage
        Document windowStage = new Document();
        // Parse window field definitions and build $setWindowFields
        
        pipeline.add(new Document("$setWindowFields", windowStage));
        
        // Add $sort if needed
        if (!query.orderBy().isEmpty()) {
            Document sort = new Document();
            query.orderBy().forEach(s -> 
                sort.put(s.field(), s.order().name().equals("ASCENDING") ? 1 : -1)
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
     * Convert MongoDB Document to Map.
     */
    private Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> map = new HashMap<>();
        doc.forEach(map::put);
        return map;
    }
}