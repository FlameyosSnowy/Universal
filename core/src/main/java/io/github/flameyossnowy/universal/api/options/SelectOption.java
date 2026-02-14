package io.github.flameyossnowy.universal.api.options;

/**
 * The select option.
 * @param option The key in the database.
 * @param operator The operator, usually defaults to "=".
 * @param value The value to compare with.
 */
public record SelectOption(String option, String operator, Object value) implements FilterOption {
}
