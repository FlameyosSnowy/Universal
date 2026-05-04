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

package io.github.flameyossnowy.universal.microservices.network.validation;

import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.Validate;
import io.github.flameyossnowy.universal.api.cache.PatternCache;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.meta.ConstraintModel;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.meta.ValidationModel;
import io.github.flameyossnowy.universal.api.utils.TemporalUtils;
import io.github.flameyossnowy.universal.api.utils.ValidationUtils;
import io.github.flameyossnowy.universal.api.utils.ValueUtils;
import io.github.flameyossnowy.universal.api.validation.BackendHint;
import io.github.flameyossnowy.universal.api.validation.ValidationException;
import io.github.flameyossnowy.universal.api.validation.ValidationMessages;
import io.github.flameyossnowy.universal.api.validation.ValidationTranslator;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Network-based implementation of {@link ValidationTranslator}.
 * Converts universal validation rules to validation specifications for remote services.
 *
 * <p>Since network-based storage delegates to remote services, this translator
 * generates validation specifications that can be sent to the remote API
 * or used for client-side pre-validation.
 *
 * @author flameyosflow
 * @version 7.2.0
 * @see ValidationTranslator
 * @since 7.2.0
 */
public class NetworkValidationTranslator<T> implements ValidationTranslator<T> {

    @Override
    public String getBackendType() {
        return "network";
    }

    @Override
    public <R> R translate(Validate.Rule rule, String field, Map<String, String> params, List<BackendHint> hints) {
        ValidationSpec spec = createValidationSpec(rule, field, params);
        @SuppressWarnings("unchecked")
        R result = (R) spec;
        return result;
    }

    @Override
    public <R> R translateConstraint(Constraint.Type type, String[] fields, Map<String, String> params, List<BackendHint> hints) {
        ConstraintSpec spec = createConstraintSpec(type, fields, params);
        @SuppressWarnings("unchecked")
        R result = (R) spec;
        return result;
    }

    /**
     * Creates a validation specification for a single rule.
     */
    private ValidationSpec createValidationSpec(Validate.Rule rule, String field, Map<String, String> params) {
        return new ValidationSpec(rule, field, new HashMap<>(params));
    }

    /**
     * Creates a constraint specification for cross-field validation.
     */
    private ConstraintSpec createConstraintSpec(Constraint.Type type, String[] fields, Map<String, String> params) {
        return new ConstraintSpec(type, fields, new HashMap<>(params));
    }

    /**
     * Converts a validation specification to an OpenAPI-style validation schema.
     *
     * @param specs the validation specifications
     * @return the OpenAPI schema fragment
     */
    public Map<String, Object> toOpenApiSchema(List<ValidationSpec> specs) {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Map<String, Object>> properties = new HashMap<>();

        for (ValidationSpec spec : specs) {
            Map<String, Object> fieldSchema = new HashMap<>();

            switch (spec.rule()) {
                case NOT_EMPTY, NOT_BLANK -> fieldSchema.put("minLength", 1);
                case POSITIVE -> {
                    fieldSchema.put("minimum", 0);
                    fieldSchema.put("exclusiveMinimum", true);
                }
                case POSITIVE_OR_ZERO -> fieldSchema.put("minimum", 0);
                case NEGATIVE -> {
                    fieldSchema.put("maximum", 0);
                    fieldSchema.put("exclusiveMaximum", true);
                }
                case MIN_LENGTH -> {
                    String min = spec.params().get("min");
                    if (min != null) {
                        fieldSchema.put("minLength", Integer.parseInt(min));
                    }
                }
                case MAX_LENGTH -> {
                    String max = spec.params().get("max");
                    if (max != null) {
                        fieldSchema.put("maxLength", Integer.parseInt(max));
                    }
                }
                case RANGE -> {
                    String min = spec.params().get("min");
                    String max = spec.params().get("max");
                    if (min != null) fieldSchema.put("minimum", Double.parseDouble(min));
                    if (max != null) fieldSchema.put("maximum", Double.parseDouble(max));
                }
                case PATTERN -> {
                    String pattern = spec.params().get("pattern");
                    if (pattern != null) {
                        fieldSchema.put("pattern", pattern);
                    }
                }
                case EMAIL -> fieldSchema.put("format", "email");
                case URL -> fieldSchema.put("format", "uri");
                case FUTURE -> fieldSchema.put("format", "date-time");
                case PAST -> fieldSchema.put("format", "date-time");
                default -> { /* No specific OpenAPI mapping */ }
            }

            properties.put(spec.field(), fieldSchema);
        }

        schema.put("properties", properties);
        return schema;
    }

    private boolean validateValue(Object value, ValidationSpec spec) {
        return switch (spec.rule()) {
            case NOT_EMPTY -> value != null && !ValueUtils.isEmpty(value);
            case NOT_BLANK -> value != null && !value.toString().trim().isEmpty();
            case POSITIVE -> value instanceof Number n && n.doubleValue() > 0;
            case POSITIVE_OR_ZERO -> value instanceof Number n && n.doubleValue() >= 0;
            case NEGATIVE -> value instanceof Number n && n.doubleValue() < 0;
            case MIN_LENGTH -> {
                String min = spec.params().get("min");
                int minLen = min != null ? Integer.parseInt(min) : 0;
                yield ValueUtils.getLength(value) >= minLen;
            }
            case MAX_LENGTH -> {
                String max = spec.params().get("max");
                int maxLen = max != null ? Integer.parseInt(max) : Integer.MAX_VALUE;
                yield ValueUtils.getLength(value) <= maxLen;
            }
            case RANGE -> {
                if (!(value instanceof Number n)) yield false;
                double val = n.doubleValue();
                String min = spec.params().get("min");
                String max = spec.params().get("max");
                boolean inRange = true;
                if (min != null) inRange = val >= Double.parseDouble(min);
                if (max != null) inRange = inRange && val <= Double.parseDouble(max);
                yield inRange;
            }
            case PATTERN -> {
                String patternStr = spec.params().get("pattern");
                if (patternStr == null || value == null) yield true;

                Pattern pattern = PatternCache.getOrCompile(patternStr);
                yield pattern.matcher(value.toString()).matches();
            }
            case EMAIL -> {
                if (value == null) yield true;
                yield ValidationUtils.isValidEmail(value.toString());
            }
            case URL -> {
                if (value == null) yield true;
                yield ValidationUtils.isValidUrl(value.toString());
            }
            default -> true;
        };
    }

    /**
     * Represents a single field validation specification.
     */
    public record ValidationSpec(Validate.Rule rule, String field, Map<String, String> params) {}

    /**
     * Represents a cross-field constraint specification.
     */
    public record ConstraintSpec(Constraint.Type type, String[] fields, Map<String, String> params) {}

    @Override
    public <ID> List<ValidationException.Violation> validate(T entity, RepositoryModel<T, ID> repositoryModel, ObjectModel<T, ID> objectModel) {
        List<ValidationException.Violation> violations = new ArrayList<>();

        // Validate each field with @Validate annotations
        for (FieldModel<T> fieldModel : repositoryModel.fields()) {
            ValidationModel validation = fieldModel.validation();
            if (validation == null || !validation.hasValidation()) continue;

            String fieldName = fieldModel.name();
            Object value = objectModel.getFieldValue(entity, fieldName);

            // Check each validation rule
            Map<String, String> params = validation.params();
            for (Validate.Rule rule : validation.rules()) {
                if (!validateValue(rule, value, params)) {
                    String message = buildErrorMessage(rule, fieldName, params);
                    violations.add(new ValidationException.Violation(fieldName, message));
                }
            }
        }

        // Validate cross-field constraints
        for (ConstraintModel constraint : repositoryModel.constraints()) {
            if (!validateConstraint(entity, constraint, objectModel)) {
                violations.add(new ValidationException.Violation(
                    String.join(", ", constraint.fields()),
                    "Cross-field constraint failed: " + constraint.type()
                ));
            }
        }

        return violations;
    }

    private boolean validateValue(Validate.Rule rule, Object value, Map<String, String> params) {
        return switch (rule) {
            case NOT_NULL -> value != null;
            case NOT_EMPTY -> value != null && !ValueUtils.isEmpty(value);
            case NOT_BLANK -> value != null && !value.toString().trim().isEmpty();
            case POSITIVE -> value instanceof Number n && n.doubleValue() > 0;
            case POSITIVE_OR_ZERO -> value instanceof Number n && n.doubleValue() >= 0;
            case NEGATIVE -> value instanceof Number n && n.doubleValue() < 0;
            case NEGATIVE_OR_ZERO -> value instanceof Number n && n.doubleValue() <= 0;
            case MIN -> {
                if (!(value instanceof Number n)) yield false;
                String min = params.get("min");
                double minVal = min != null ? Double.parseDouble(min) : 0;
                yield n.doubleValue() >= minVal;
            }
            case MAX -> {
                if (!(value instanceof Number n)) yield false;
                String max = params.get("max");
                double maxVal = max != null ? Double.parseDouble(max) : Double.MAX_VALUE;
                yield n.doubleValue() <= maxVal;
            }
            case MIN_LENGTH -> {
                String min = params.get("min");
                int minLen = min != null ? Integer.parseInt(min) : 0;
                yield ValueUtils.getLength(value) >= minLen;
            }
            case MAX_LENGTH -> {
                String max = params.get("max");
                int maxLen = max != null ? Integer.parseInt(max) : Integer.MAX_VALUE;
                yield ValueUtils.getLength(value) <= maxLen;
            }
            case RANGE -> {
                if (!(value instanceof Number n)) yield false;
                String min = params.get("min");
                String max = params.get("max");
                double val = n.doubleValue();
                double minVal = min != null ? Double.parseDouble(min) : Double.MIN_VALUE;
                double maxVal = max != null ? Double.parseDouble(max) : Double.MAX_VALUE;
                yield val >= minVal && val <= maxVal;
            }
            case PATTERN -> {
                String patternStr = params.get("pattern");
                if (patternStr == null || value == null) yield true;
                Pattern pattern = PatternCache.getOrCompile(patternStr);
                yield pattern.matcher(value.toString()).matches();
            }
            case EMAIL -> {
                if (value == null) yield true;
                yield ValidationUtils.isValidEmail(value.toString());
            }
            case URL -> {
                if (value == null) yield true;
                yield ValidationUtils.isValidUrl(value.toString());
            }
            case UUID -> {
                if (value == null) yield true;
                try {
                    UUID.fromString(value.toString());
                    yield true;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
            case CREDIT_CARD -> {
                if (value == null) yield true;
                yield ValidationUtils.isValidCreditCard(value.toString());
            }
            case PAST -> {
                if (value == null) yield true;
                yield TemporalUtils.isInPast(value);
            }
            case FUTURE -> {
                if (value == null) yield true;
                yield TemporalUtils.isInFuture(value);
            }
            case PAST_OR_PRESENT -> {
                if (value == null) yield true;
                yield TemporalUtils.isPastOrPresent(value);
            }
            case FUTURE_OR_PRESENT -> {
                if (value == null) yield true;
                yield TemporalUtils.isFutureOrPresent(value);
            }
            case DIGITS_ONLY -> {
                if (value == null) yield true;
                yield ValidationUtils.isDigitsOnly(value.toString());
            }
            case ALPHA_ONLY -> {
                if (value == null) yield true;
                yield ValidationUtils.isAlphaOnly(value.toString());
            }
            case ALPHANUMERIC -> {
                if (value == null) yield true;
                yield ValidationUtils.isAlphanumeric(value.toString());
            }
            case UPPERCASE -> {
                if (value == null) yield true;
                String s = value.toString();
                yield s.equals(s.toUpperCase());
            }
            case LOWERCASE -> {
                if (value == null) yield true;
                String s = value.toString();
                yield s.equals(s.toLowerCase());
            }
            case UNIQUE -> true; // Handled at server level
            case REQUIRED -> value != null && !ValueUtils.isEmpty(value);
            case REFERENCE_EXISTS -> value != null;
        };
    }

    private <ID> boolean validateConstraint(T entity, ConstraintModel constraint, ObjectModel<T, ID> objectModel) {
        List<String> fields = constraint.fields();
        if (fields == null || fields.size() < 2) return true;

        Object[] values = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            values[i] = objectModel.getFieldValue(entity, fields.get(i));
        }

        return switch (constraint.type()) {
            case ORDERED -> {
                for (int i = 0; i < values.length - 1; i++) {
                    if (ValueUtils.compareValues(values[i], values[i + 1]) >= 0) yield false;
                }
                yield true;
            }
            case UNIQUE_COMBINATION -> true; // Handled at server level
            case REQUIRES -> {
                // If first field exists, all others must exist
                if (values[0] == null || ValueUtils.isEmpty(values[0])) yield true;
                for (int i = 1; i < values.length; i++) {
                    if (values[i] == null || ValueUtils.isEmpty(values[i])) yield false;
                }
                yield true;
            }
            case MUTUALLY_EXCLUSIVE -> {
                // Only one of the fields can be non-null
                int nonNullCount = 0;
                for (Object value : values) {
                    if (value != null && !ValueUtils.isEmpty(value)) nonNullCount++;
                }
                yield nonNullCount <= 1;
            }
            case CONDITIONAL -> true; // Custom logic, skip here
            case REFERENTIAL_INTEGRITY -> false;
        };
    }

    private static String buildErrorMessage(Validate.Rule rule, String field, Map<String, String> params) {
        return ValidationMessages.buildErrorMessage(rule, field, params);
    }
}
