/*
 * Copyright (C) 2026 flameyosflow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.flameyossnowy.universal.api.validation;

import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Utility class for building validation error messages.
 * Provides standardized error messages for all validation rules.
 *
 * @author flameyosflow
 * @version 7.2.0
 * @since 7.2.0
 */
public final class ValidationMessages {

    private ValidationMessages() {
        // Utility class
    }

    /**
     * Builds an error message for a validation rule violation.
     *
     * @param rule the validation rule
     * @param field the field name
     * @param params the validation parameters
     * @return the error message
     */
    @NotNull
    public static String buildErrorMessage(@NotNull Validate.Rule rule, @NotNull String field, @NotNull Map<String, String> params) {
        return switch (rule) {
            case NOT_NULL -> field + " must not be null";
            case NOT_EMPTY -> field + " must not be empty";
            case NOT_BLANK -> field + " must not be blank";
            case POSITIVE -> field + " must be positive";
            case POSITIVE_OR_ZERO -> field + " must be positive or zero";
            case NEGATIVE -> field + " must be negative";
            case NEGATIVE_OR_ZERO -> field + " must be negative or zero";
            case MIN -> field + " must be at least " + params.get("min");
            case MAX -> field + " must be at most " + params.get("max");
            case MIN_LENGTH -> field + " must have at least " + params.get("min") + " characters";
            case MAX_LENGTH -> field + " must have at most " + params.get("max") + " characters";
            case RANGE -> field + " must be between " + params.get("min") + " and " + params.get("max");
            case PATTERN -> field + " must match pattern: " + params.get("pattern");
            case EMAIL -> field + " must be a valid email address";
            case URL -> field + " must be a valid URL";
            case UUID -> field + " must be a valid UUID";
            case CREDIT_CARD -> field + " must be a valid credit card number";
            case PAST -> field + " must be in the past";
            case FUTURE -> field + " must be in the future";
            case PAST_OR_PRESENT -> field + " must be in the past or present";
            case FUTURE_OR_PRESENT -> field + " must be in the future or present";
            case DIGITS_ONLY -> field + " must contain only digits";
            case ALPHA_ONLY -> field + " must contain only letters";
            case ALPHANUMERIC -> field + " must contain only letters and numbers";
            case UPPERCASE -> field + " must be uppercase";
            case LOWERCASE -> field + " must be lowercase";
            case UNIQUE -> field + " must be unique";
            case REQUIRED -> field + " is required";
            case REFERENCE_EXISTS -> field + " must be an existing reference";
        };
    }

    /**
     * Builds an error message for a constraint violation.
     *
     * @param type the constraint type
     * @param fields the field names involved
     * @param params the constraint parameters
     * @return the error message
     */
    @NotNull
    public static String buildConstraintErrorMessage(@NotNull Constraint.Type type, @NotNull String[] fields, @NotNull Map<String, String> params) {
        String fieldList = String.join(", ", fields);
        return switch (type) {
            case UNIQUE_COMBINATION -> "Combination of fields must be unique: " + fieldList;
            case REQUIRES -> fields[0] + " requires: " + String.join(", ", java.util.Arrays.copyOfRange(fields, 1, fields.length));
            case MUTUALLY_EXCLUSIVE -> "Only one of these fields can be set: " + fieldList;
            case ORDERED -> fields[0] + " must be less than " + fields[1];
            case CONDITIONAL -> "Conditional constraint violated for: " + fieldList;
            case REFERENTIAL_INTEGRITY -> "Referential integrity violated for: " + fieldList;
        };
    }

    /**
     * Builds a generic error message with field name.
     *
     * @param field the field name
     * @param message the message template
     * @return the formatted message
     */
    @NotNull
    public static String forField(@NotNull String field, @NotNull String message) {
        return field + " " + message;
    }
}
