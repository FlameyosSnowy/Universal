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

package io.github.flameyossnowy.universal.microservices.file.validation;

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
import io.github.flameyossnowy.universal.api.validation.ValidationTranslator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.github.flameyossnowy.universal.api.utils.ValueUtils.getLength;
import static io.github.flameyossnowy.universal.api.validation.ValidationMessages.buildErrorMessage;

/**
 * File-based implementation of {@link ValidationTranslator}.
 * Converts universal validation rules to Java predicates for in-memory validation.
 *
 * <p>Since file-based storage doesn't have a database engine to enforce constraints,
 * this translator generates Java {@link Predicate} instances that can be used to
 * validate entities before writing to files.
 *
 * @param <T> the entity type
 * @author flameyosflow
 * @version 2.0.0
 * @see ValidationTranslator
 * @since 2.0.0
 */
public class FileValidationTranslator<T> implements ValidationTranslator<T> {

    private ObjectModel<T, ?> objectModel;

    @Override
    public String getBackendType() {
        return "file";
    }

    @Override
    public <R> R translate(Validate.Rule rule, String field, Map<String, String> params, List<BackendHint> hints) {
        @SuppressWarnings("unchecked")
        R result = (R) createPredicate(rule, field, params);
        return result;
    }

    @Override
    public <R> R translateConstraint(Constraint.Type type, String[] fields, Map<String, String> params, List<BackendHint> hints) {
        @SuppressWarnings("unchecked")
        R result = (R) createConstraintPredicate(type, fields, params);
        return result;
    }

    /**
     * Creates a predicate for validating a specific rule on a field.
     */
    private Predicate<T> createPredicate(Validate.Rule rule, String fieldName, Map<String, String> params) {
        return entity -> {
            Object value = objectModel != null ? objectModel.getFieldValue(entity, fieldName) : null;

            return switch (rule) {
                case NOT_EMPTY -> value != null && !ValueUtils.isEmpty(value);
                case NOT_BLANK -> value != null && !value.toString().trim().isEmpty();
                case POSITIVE -> value instanceof Number n && n.doubleValue() > 0;
                case POSITIVE_OR_ZERO -> value instanceof Number n && n.doubleValue() >= 0;
                case NEGATIVE -> value instanceof Number n && n.doubleValue() < 0;
                case NEGATIVE_OR_ZERO -> value instanceof Number n && n.doubleValue() <= 0;
                case NOT_NULL -> value != null;
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
                    yield getLength(value) >= minLen;
                }
                case MAX_LENGTH -> {
                    String max = params.get("max");
                    int maxLen = max != null ? Integer.parseInt(max) : Integer.MAX_VALUE;
                    yield getLength(value) <= maxLen;
                }
                case RANGE -> {
                    String min = params.get("min");
                    String max = params.get("max");
                    double val = value instanceof Number n ? n.doubleValue() : 0;
                    boolean inRange = true;
                    if (min != null) inRange = val >= Double.parseDouble(min);
                    if (max != null) inRange = inRange && val <= Double.parseDouble(max);
                    yield inRange;
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
                case UNIQUE -> true; // Handled by uniqueness check before write
                case FUTURE -> {
                    if (value == null) yield true;
                    Instant now = Instant.now();
                    Instant fieldValue = TemporalUtils.parseInstant(value);
                    yield fieldValue != null && fieldValue.isAfter(now);
                }
                case PAST -> {
                    if (value == null) yield true;
                    Instant now = Instant.now();
                    Instant fieldValue = TemporalUtils.parseInstant(value);
                    yield fieldValue != null && fieldValue.isBefore(now);
                }
                case FUTURE_OR_PRESENT -> {
                    if (value == null) yield true;
                    Instant now = Instant.now();
                    Instant fieldValue = TemporalUtils.parseInstant(value);
                    yield fieldValue != null && !fieldValue.isBefore(now);
                }
                case PAST_OR_PRESENT -> {
                    if (value == null) yield true;
                    Instant now = Instant.now();
                    Instant fieldValue = TemporalUtils.parseInstant(value);
                    yield fieldValue != null && !fieldValue.isAfter(now);
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
                case REQUIRED -> value != null && !ValueUtils.isEmpty(value);
                case REFERENCE_EXISTS -> true; // Handled by relationship handler
            };
        };
    }

    /**
     * Creates a predicate for cross-field constraints.
     */
    @SuppressWarnings("unchecked")
    private Predicate<T> createConstraintPredicate(Constraint.Type type, String[] fields, Map<String, String> params) {
        if (fields == null || fields.length == 0) {
            return entity -> true;
        }

        return entity -> switch (type) {
            case UNIQUE_COMBINATION -> true; // Handled by index uniqueness
            case REQUIRES -> {
                // If first field exists, all others must exist
                Object first = objectModel != null ? objectModel.getFieldValue(entity, fields[0]) : null;
                if (first == null || ValueUtils.isEmpty(first)) yield true;

                boolean allExist = true;
                for (int i = 1; i < fields.length; i++) {
                    Object val = objectModel != null ? objectModel.getFieldValue(entity, fields[i]) : null;
                    if (val == null || ValueUtils.isEmpty(val)) {
                        allExist = false;
                        break;
                    }
                }
                yield allExist;
            }
            case MUTUALLY_EXCLUSIVE -> {
                // Only one of the fields can be non-null
                int nonNullCount = 0;
                for (String field : fields) {
                    Object val = objectModel != null ? objectModel.getFieldValue(entity, field) : null;
                    if (val != null && !ValueUtils.isEmpty(val)) {
                        nonNullCount++;
                    }
                }
                yield nonNullCount <= 1;
            }
            case ORDERED -> {
                // First field must be less than second field
                if (fields.length >= 2) {
                    Object first = objectModel != null ? objectModel.getFieldValue(entity, fields[0]) : null;
                    Object second = objectModel != null ? objectModel.getFieldValue(entity, fields[1]) : null;
                    if (first == null || second == null) yield true;
                    yield ValueUtils.compareValues(first, second) < 0;
                }
                yield true;
            }
            case CONDITIONAL -> {
                // Custom condition from params
                String condition = params.get("condition");
                if (condition == null) yield true;
                // Custom condition evaluation would be implemented here
                yield true;
            }
            case REFERENTIAL_INTEGRITY -> true; // Handled by relationship handler
        };
    }

    /**
     * Combines multiple field validation predicates into a single predicate.
     *
     * @param fieldPredicates map of field names to their validation predicates
     * @return a combined predicate that validates all fields
     */
    public final Predicate<T> combineFieldValidations(Map<String, List<Predicate<T>>> fieldPredicates) {
        return entity -> {
            for (List<Predicate<T>> predicates : fieldPredicates.values()) {
                for (Predicate<T> predicate : predicates) {
                    if (!predicate.test(entity)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Override
    public <ID> List<ValidationException.Violation> validate(T entity, RepositoryModel<T, ID> repositoryModel, ObjectModel<T, ID> objectModel) {
        this.objectModel = objectModel;
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
                yield getLength(value) >= minLen;
            }
            case MAX_LENGTH -> {
                String max = params.get("max");
                int maxLen = max != null ? Integer.parseInt(max) : Integer.MAX_VALUE;
                yield getLength(value) <= maxLen;
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
                Instant instant = TemporalUtils.parseInstant(value);
                yield instant != null && instant.isBefore(Instant.now());
            }
            case FUTURE -> {
                if (value == null) yield true;
                Instant instant = TemporalUtils.parseInstant(value);
                yield instant != null && instant.isAfter(Instant.now());
            }
            case PAST_OR_PRESENT -> {
                if (value == null) yield true;
                Instant instant = TemporalUtils.parseInstant(value);
                yield instant != null && !instant.isAfter(Instant.now());
            }
            case FUTURE_OR_PRESENT -> {
                if (value == null) yield true;
                Instant instant = TemporalUtils.parseInstant(value);
                yield instant != null && !instant.isBefore(Instant.now());
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
            case UNIQUE -> true; // Handled at repository level
            case REQUIRED -> value != null && !ValueUtils.isEmpty(value);
            case REFERENCE_EXISTS -> false;
        };
    }

    @SuppressWarnings("unchecked")
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
            case UNIQUE_COMBINATION -> true; // Handled at database level
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
            case REFERENTIAL_INTEGRITY -> true;
        };
    }

}
