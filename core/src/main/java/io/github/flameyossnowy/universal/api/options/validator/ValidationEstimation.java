package io.github.flameyossnowy.universal.api.options.validator;

public record ValidationEstimation(ValidationResult result, String reason) {
    public static final ValidationEstimation PASS = new ValidationEstimation(ValidationResult.PASS, "");

    public static ValidationEstimation fail(String reason) {
        return new ValidationEstimation(ValidationResult.FAIL, reason);
    }

    public boolean isFail() {
        return result == ValidationResult.FAIL;
    }
}