package io.github.flameyossnowy.universal.api.options.validator;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record ValidationEstimation(ValidationResult result, String reason) {
    public static final ValidationEstimation PASS = new ValidationEstimation(ValidationResult.PASS, "");

    @Contract("_ -> new")
    public static @NotNull ValidationEstimation fail(String reason) {
        return new ValidationEstimation(ValidationResult.FAIL, reason);
    }

    public boolean isFail() {
        return result == ValidationResult.FAIL;
    }
}