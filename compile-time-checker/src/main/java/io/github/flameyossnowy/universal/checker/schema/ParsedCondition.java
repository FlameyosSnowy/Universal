package io.github.flameyossnowy.universal.checker.schema;

public record ParsedCondition(
    String field,
    String operator,
    Object value
) {}