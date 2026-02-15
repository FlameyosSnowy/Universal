package io.github.flameyossnowy.universal.api.options;

/**
 * Common interface for field definitions (regular, aggregate, window).
 */
public interface FieldDefinition {
    String getAlias();
    String getFieldName();
}