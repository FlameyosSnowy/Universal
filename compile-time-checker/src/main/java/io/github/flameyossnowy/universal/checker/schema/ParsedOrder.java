package io.github.flameyossnowy.universal.checker.schema;

public record ParsedOrder(
    String field,
    Direction direction
) {
    public enum Direction { ASC, DESC }
}