package io.github.flameyossnowy.universal.checker.schema;

public record Condition(
    String field,
    Operator operator,
    Object value
) {
    public enum Operator {
        EQ,
        NEQ,
        GT,
        GTE,
        LT,
        LTE,
        LIKE,
        IN,
        IS_NULL
    }
}