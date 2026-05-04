package io.github.flameyossnowy.universal.api.validation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when entity validation fails.
 * Contains details about which fields failed validation and why.
 */
public class ValidationException extends RuntimeException {
    private final String entityType;
    private final List<Violation> violations;

    public ValidationException(@NotNull String entityType, @NotNull List<Violation> violations) {
        super(buildMessage(entityType, violations));
        this.entityType = entityType;
        this.violations = Collections.unmodifiableList(violations);
    }

    public ValidationException(@NotNull String entityType, @NotNull String field, @NotNull String message) {
        this(entityType, List.of(new Violation(field, message)));
    }

    public @NotNull String getEntityType() {
        return entityType;
    }

    public @NotNull List<Violation> getViolations() {
        return violations;
    }

    private static String buildMessage(String entityType, List<Violation> violations) {
        StringBuilder sb = new StringBuilder("Validation failed for ");
        sb.append(entityType).append(":\n");
        for (Violation v : violations) {
            sb.append("  - ").append(v.display()).append('\n');
        }
        return sb.toString();
    }

    public record Violation(@NotNull String field, @NotNull String message) {
        @NotNull
        @Contract(pure = true)
        public String display() {
            return field + ": " + message;
        }
    }
}
