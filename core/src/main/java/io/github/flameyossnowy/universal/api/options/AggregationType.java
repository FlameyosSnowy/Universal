package io.github.flameyossnowy.universal.api.options;

/**
 * Types of aggregation functions supported across SQL and MongoDB.
 */
public enum AggregationType {
    // Basic aggregations
    COUNT,
    COUNT_DISTINCT,
    SUM,
    AVG,
    MIN,
    MAX,
    
    // Conditional aggregations (using CASE WHEN in SQL, $cond in MongoDB)
    COUNT_IF,
    SUM_IF,
    
    // String aggregations
    STRING_AGG,      // GROUP_CONCAT (MySQL), STRING_AGG (PostgreSQL), $reduce (MongoDB)
    
    // Array/JSON aggregations
    ARRAY_LENGTH,    // jsonb_array_length (PostgreSQL), $size (MongoDB)
    JSON_ARRAY_AGG,  // json_agg (PostgreSQL), $push (MongoDB)
    JSON_OBJECT_AGG, // json_object_agg (PostgreSQL), $addToSet (MongoDB)
    
    // Statistical
    STDDEV,
    VARIANCE,
    
    // First/Last
    FIRST,
    LAST
}
