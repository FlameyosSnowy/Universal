package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.Nullable;

/**
 * Represents an aggregated field with optional conditions.
 * 
 * <pre>{@code
 * field("status").countIf(eq("active")).as("active_count")
 * // Becomes: COUNT(CASE WHEN status = 'active' THEN 1 END) AS active_count
 * }</pre>
 */
public record AggregateFieldDefinition(
    String field,
    @Nullable String jsonPath,
    AggregationType aggregationType,
    @Nullable FilterOption condition,
    String alias
) implements FieldDefinition {
    
    @Override
    public String getAlias() {
        return alias;
    }
    
    @Override
    public String getFieldName() {
        return field;
    }
    
    public boolean isConditional() {
        return condition != null;
    }
    
    public boolean isJson() {
        return jsonPath != null;
    }
}