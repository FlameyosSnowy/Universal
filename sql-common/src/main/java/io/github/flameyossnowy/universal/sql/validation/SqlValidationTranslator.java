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

package io.github.flameyossnowy.universal.sql.validation;

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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.github.flameyossnowy.universal.api.utils.ValueUtils.getLength;

/**
 * SQL implementation of {@link ValidationTranslator}.
 * Converts universal validation rules to SQL CHECK constraints.
 *
 * <p>Example translations:
 * <ul>
 *   <li>{@code NOT_BLANK} → {@code LENGTH(field) > 0 AND field NOT REGEXP '^\\s*$'}</li>
 *   <li>{@code RANGE(min=18,max=120)} → {@code field BETWEEN 18 AND 120}</li>
 *   <li>{@code PATTERN='^[A-Z]{2}\\d{4}$'} → {@code field REGEXP '^[A-Z]{2}\\d{4}$'}</li>
 * </ul>
 *
 * @author flameyosflow
 * @version 7.2.0
 * @see ValidationTranslator
 * @since 7.2.0
 */
public class SqlValidationTranslator<T> implements ValidationTranslator<T> {

    @Override
    public String getBackendType() {
        return "sql";
    }

    @Override
    public <R> R translate(Validate.Rule rule, String field, Map<String, String> params, List<BackendHint> hints) {
        String sql = switch (rule) {
            case NOT_EMPTY, REQUIRED -> field + " IS NOT NULL AND LENGTH(CAST(" + field + " AS VARCHAR)) > 0";
            case NOT_BLANK -> field + " IS NOT NULL AND " + field + " NOT REGEXP '^\\s*$'";
            case POSITIVE -> field + " > 0";
            case POSITIVE_OR_ZERO -> field + " >= 0";
            case NEGATIVE -> field + " < 0";
            case MIN_LENGTH -> {
                String min = params.get("min");
                yield min != null
                    ? "LENGTH(CAST(" + field + " AS VARCHAR)) >= " + min
                    : field + " IS NOT NULL";
            }
            case MAX_LENGTH -> {
                String max = params.get("max");
                yield max != null
                    ? "LENGTH(CAST(" + field + " AS VARCHAR)) <= " + max
                    : field + " IS NOT NULL";
            }
            case RANGE -> {
                String min = params.get("min");
                String max = params.get("max");
                if (min != null && max != null) {
                    yield field + " BETWEEN " + min + " AND " + max;
                } else if (min != null) {
                    yield field + " >= " + min;
                } else if (max != null) {
                    yield field + " <= " + max;
                } else {
                    yield field + " IS NOT NULL";
                }
            }
            case PATTERN -> {
                String pattern = params.get("pattern");
                yield pattern != null
                    ? field + " REGEXP '" + escapeSqlString(pattern) + "'"
                    : field + " IS NOT NULL";
            }
            case EMAIL -> field + " REGEXP '^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'";
            case URL -> field + " REGEXP '^https?://.+$'";
            case UNIQUE -> null; // Handled by UNIQUE constraint, not CHECK
            case FUTURE -> field + " > CURRENT_TIMESTAMP";
            case PAST -> field + " < CURRENT_TIMESTAMP";
            case REFERENCE_EXISTS -> null; // Handled by FOREIGN KEY constraint
            case NOT_NULL -> field + " IS NOT NULL";
            case MIN -> {
                String min = params.get("min");
                yield min != null ? field + " >= " + min : field + " IS NOT NULL";
            }
            case MAX -> {
                String max = params.get("max");
                yield max != null ? field + " <= " + max : field + " IS NOT NULL";
            }
            case NEGATIVE_OR_ZERO -> field + " <= 0";
            case UUID -> field + " REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'";
            case CREDIT_CARD -> null; // Too complex for CHECK constraint
            case PAST_OR_PRESENT -> field + " <= CURRENT_TIMESTAMP";
            case FUTURE_OR_PRESENT -> field + " >= CURRENT_TIMESTAMP";
            case DIGITS_ONLY -> field + " REGEXP '^[0-9]+$'";
            case ALPHA_ONLY -> field + " REGEXP '^[a-zA-Z]+$'";
            case ALPHANUMERIC -> field + " REGEXP '^[a-zA-Z0-9]+$'";
            case UPPERCASE -> field + " = UPPER(" + field + ")";
            case LOWERCASE -> field + " = LOWER(" + field + ")";
        };

        @SuppressWarnings("unchecked")
        R result = (R) sql;
        return result;
    }

    @Override
    public <R> R translateConstraint(Constraint.Type type, String[] fields, Map<String, String> params, List<BackendHint> hints) {
        if (fields == null || fields.length == 0) {
            return null;
        }

        String sql = switch (type) {
            case UNIQUE_COMBINATION -> "UNIQUE(" + String.join(", ", fields) + ")";
            case REQUIRES -> {
                // If first field exists, all others must exist
                StringBuilder sb = new StringBuilder(32);
                sb.append("(").append(fields[0]).append(" IS NOT NULL)");
                for (int i = 1; i < fields.length; i++) {
                    sb.append(" AND (").append(fields[i]).append(" IS NOT NULL)");
                }
                yield sb.toString();
            }
            case MUTUALLY_EXCLUSIVE -> {
                // Only one of the fields can be non-null
                StringBuilder sb = new StringBuilder(64);
                sb.append("(");
                for (int i = 0; i < fields.length; i++) {
                    if (i > 0) sb.append(" + ");
                    sb.append("CASE WHEN ").append(fields[i]).append(" IS NOT NULL THEN 1 ELSE 0 END");
                }
                sb.append(") <= 1");
                yield sb.toString();
            }
            case ORDERED -> {
                // First field must be less than second field
                if (fields.length >= 2) {
                    yield fields[0] + " < " + fields[1];
                }
                yield null;
            }
            case CONDITIONAL -> params.get("expression"); // Custom expression from params
            case REFERENTIAL_INTEGRITY -> null; // Handled by FOREIGN KEY
        };

        @SuppressWarnings("unchecked")
        R result = (R) sql;
        return result;
    }

    @Override
    public boolean supports(Validate.Rule rule) {
        return rule != Validate.Rule.UNIQUE && rule != Validate.Rule.REFERENCE_EXISTS;
    }

    @Override
    public boolean supports(Constraint.Type type) {
        return type != Constraint.Type.REFERENTIAL_INTEGRITY;
    }

    /**
     * Escapes single quotes in a string for SQL safety.
     */
    private static String escapeSqlString(String value) {
        return value.replace("'", "''");
    }

    /**
     * Generates a complete CHECK constraint clause.
     *
     * @param constraintName the name of the constraint
     * @param condition the SQL condition
     * @return the complete CHECK constraint SQL
     */
    public static String generateCheckConstraint(String constraintName, String condition) {
        return "CONSTRAINT " + constraintName + " CHECK (" + condition + ")";
    }

    /**
     * Generates a CHECK constraint name for a field.
     *
     * @param tableName the table name
     * @param fieldName the field name
     * @param rule the validation rule
     * @return the constraint name
     */
    public static String generateConstraintName(String tableName, String fieldName, Validate.Rule rule) {
        return "chk_" + tableName + "_" + fieldName + "_" + rule.name().toLowerCase();
    }

    @Override
    public <ID> List<ValidationException.Violation> validate(T entity, RepositoryModel<T, ID> repositoryModel, ObjectModel<T, ID> objectModel) {
        List<ValidationException.Violation> violations = new java.util.ArrayList<>();

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
            case NOT_EMPTY -> value != null && !isEmpty(value);
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
                    java.util.UUID.fromString(value.toString());
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
            case UNIQUE -> true; // Handled at database level
            case REQUIRED -> value != null && !ValueUtils.isEmpty(value);
            case REFERENCE_EXISTS -> value != null; // Basic null check, FK constraint handles rest
        };
    }

    @SuppressWarnings("unchecked")
    private <ID> boolean validateConstraint(T entity, ConstraintModel constraint, ObjectModel<T, ID> objectModel) {
        java.util.List<String> fields = constraint.fields();
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
            case REFERENTIAL_INTEGRITY -> false; // Cannot validate without DB lookup
        };
    }





    private String buildErrorMessage(Validate.Rule rule, String field, Map<String, String> params) {
        return ValidationMessages.buildErrorMessage(rule, field, params);
    }

}
