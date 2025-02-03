package io.github.flameyossnowy.universal.api.options;

/**
 * @param joinType INNER, LEFT, RIGHT, FULL
 */
public record JoinOption(String joinType, String targetTable, String onCondition) {
}
