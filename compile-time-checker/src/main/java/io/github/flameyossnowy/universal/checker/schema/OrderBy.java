package io.github.flameyossnowy.universal.checker.schema;

public record OrderBy(
    String field,
    Direction direction
) {
    public enum Direction {
        ASC,
        DESC
    }
}