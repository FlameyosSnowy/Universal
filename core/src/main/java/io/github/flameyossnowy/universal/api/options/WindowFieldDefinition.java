package io.github.flameyossnowy.universal.api.options;

import java.util.List;

/**
 * Immutable window function specification.
 */
public record WindowFieldDefinition(
    String field,
    WindowFunctionType functionType,
    List<String> partitionBy,
    List<SortOption> orderBy,
    String frameStart,
    String frameEnd,
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
}