package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.Nullable;

/**
 * Filter option that includes aggregation metadata.
 */
public record AggregateFilterOption(
    String field,
    @Nullable String jsonPath,
    String operator,
    @Nullable Object value,
    AggregationType aggregationType,
    @Nullable FilterOption condition,
    @Nullable String alias
) implements FilterOption {
    
    public boolean isAggregate() {
        return aggregationType != null;
    }
    
    public boolean isConditional() {
        return condition != null;
    }
}