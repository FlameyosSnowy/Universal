package io.github.flameyossnowy.universal.api.options;

/**
 * The sort option
 * @param field The field
 * @param order The order
 */
public record SortOption(String field, SortOrder order) {
}
