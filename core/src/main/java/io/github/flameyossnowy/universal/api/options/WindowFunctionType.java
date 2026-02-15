package io.github.flameyossnowy.universal.api.options;

/**
 * Types of window functions supported.
 */
public enum WindowFunctionType {
    ROW_NUMBER,
    RANK,
    DENSE_RANK,
    NTILE,
    LAG,
    LEAD,
    FIRST_VALUE,
    LAST_VALUE,
    NTH_VALUE,
    
    // Aggregate window functions
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX
}