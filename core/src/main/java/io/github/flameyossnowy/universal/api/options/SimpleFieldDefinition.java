package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.Nullable;

/**
 * Simple field definition for basic fields without aggregation.
 */
public record SimpleFieldDefinition(
    String field,
    @Nullable String alias
) implements FieldDefinition {
    
    @Override
    public String getAlias() {
        return alias != null ? alias : field;
    }
    
    @Override
    public String getFieldName() {
        return field;
    }
}
