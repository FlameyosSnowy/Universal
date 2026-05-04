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

package io.github.flameyossnowy.universal.mongodb.validation;

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
import org.bson.Document;

import java.lang.reflect.Array;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.github.flameyossnowy.universal.api.utils.ValueUtils.getLength;
import static io.github.flameyossnowy.universal.api.utils.ValueUtils.isEmpty;

/**
 * MongoDB implementation of {@link ValidationTranslator}.
 * Converts universal validation rules to MongoDB schema validation documents.
 *
 * <p>Example translations:
 * <ul>
 *   <li>{@code NOT_BLANK} → {@code {field: {$exists: true, $ne: ""}}}</li>
 *   <li>{@code RANGE(min=18,max=120)} → {@code {field: {$gte: 18, $lte: 120}}}</li>
 *   <li>{@code PATTERN='^[A-Z]{2}\d{4}$'} → {@code {field: {$regex: '^[A-Z]{2}\d{4}$'}}}</li>
 * </ul>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @see ValidationTranslator
 * @since 2.0.0
 */
public class MongoValidationTranslator<T> implements ValidationTranslator<T> {

    @Override
    public String getBackendType() {
        return "mongodb";
    }

    @Override
    public <R> R translate(Validate.Rule rule, String field, Map<String, String> params, List<BackendHint> hints) {
        Document result = switch (rule) {
            case NOT_EMPTY, REQUIRED -> new Document(field, new Document("$exists", true)
                .append("$ne", ""));
            case NOT_BLANK -> new Document(field, new Document("$exists", true)
                .append("$type", "string")
                .append("$not", new Document("$regex", "^\\s*$")));
            case POSITIVE -> new Document(field, new Document("$gt", 0));
            case POSITIVE_OR_ZERO -> new Document(field, new Document("$gte", 0));
            case NEGATIVE -> new Document(field, new Document("$lt", 0));
            case MIN_LENGTH -> {
                String min = params.get("min");
                if (min != null) {
                    yield new Document("$expr", new Document("$gte", List.of(
                        new Document("$strLenCP", "$" + field),
                        Integer.parseInt(min)
                    )));
                }
                yield new Document(field, new Document("$exists", true));
            }
            case MAX_LENGTH -> {
                String max = params.get("max");
                if (max != null) {
                    yield new Document("$expr", new Document("$lte", List.of(
                        new Document("$strLenCP", "$" + field),
                        Integer.parseInt(max)
                    )));
                }
                yield new Document(field, new Document("$exists", true));
            }
            case RANGE -> {
                Document rangeDoc = new Document();
                String min = params.get("min");
                String max = params.get("max");
                if (min != null) {
                    rangeDoc.append("$gte", parseNumber(min));
                }
                if (max != null) {
                    rangeDoc.append("$lte", parseNumber(max));
                }
                yield rangeDoc.isEmpty()
                    ? new Document(field, new Document("$exists", true))
                    : new Document(field, rangeDoc);
            }
            case PATTERN -> {
                String pattern = params.get("pattern");
                if (pattern != null) {
                    yield new Document(field, new Document("$regex", pattern));
                }
                yield new Document(field, new Document("$exists", true));
            }
            case EMAIL -> new Document(field, new Document("$regex",
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"));
            case URL -> new Document(field, new Document("$regex", "^https?://.+$"));
            case UUID -> new Document(field, new Document("$regex", "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
            case CREDIT_CARD -> new Document(field, new Document("$regex", "^\\d{16}$"));
            case UNIQUE -> null; // Handled by unique index
            case FUTURE -> new Document(field, new Document("$gt", "$currentDate"));
            case PAST -> new Document(field, new Document("$lt", "$currentDate"));
            case REFERENCE_EXISTS -> null; // Handled by application-level check
            case NEGATIVE_OR_ZERO -> new Document(field, new Document("$lte", 0));
            case MIN -> {
                String min = params.get("min");
                if (min != null) {
                    yield new Document(field, new Document("$gte", parseNumber(min)));
                }
                yield new Document(field, new Document("$exists", true));
            }
            case MAX -> {
                String max = params.get("max");
                if (max != null) {
                    yield new Document(field, new Document("$lte", parseNumber(max)));
                }
                yield new Document(field, new Document("$exists", true));
            }
            case NOT_NULL -> new Document(field, new Document("$exists", true));
            case PAST_OR_PRESENT -> new Document(field, new Document("$gte", "$currentDate"));
            case FUTURE_OR_PRESENT -> new Document(field, new Document("$lte", "$currentDate"));
            case DIGITS_ONLY -> new Document(field, new Document("$regex", "^\\d+$"));
            case ALPHA_ONLY -> new Document(field, new Document("$regex", "^[a-zA-Z]+$"));
            case ALPHANUMERIC -> new Document(field, new Document("$regex", "^[a-zA-Z0-9]+$"));
            case UPPERCASE -> new Document(field, new Document("$regex", "^[A-Z]+$"));
            case LOWERCASE -> new Document(field, new Document("$regex", "^[a-z]+$"));
        };

        @SuppressWarnings("unchecked")
        R typedResult = (R) result;
        return typedResult;
    }

    @Override
    public <R> R translateConstraint(Constraint.Type type, String[] fields, Map<String, String> params, List<BackendHint> hints) {
        if (fields == null || fields.length == 0) {
            return null;
        }

        Document result = switch (type) {
            case UNIQUE_COMBINATION -> {
                // Unique combination is handled by creating a compound unique index
                Document indexKeys = new Document();
                for (String field : fields) {
                    indexKeys.append(field, 1);
                }
                yield new Document("unique", true).append("key", indexKeys);
            }
            case REQUIRES -> {
                // If first field exists, all others must exist
                List<Document> allOf = new ArrayList<>();
                for (String field : fields) {
                    allOf.add(new Document(field, new Document("$exists", true)));
                }
                yield new Document("$and", allOf);
            }
            case MUTUALLY_EXCLUSIVE -> {
                // Only one of the fields can be non-null
                List<Document> oneOf = new ArrayList<>();
                for (String field : fields) {
                    oneOf.add(new Document(field, new Document("$exists", true).append("$ne", null)));
                }
                yield new Document("$or", oneOf);
            }
            case ORDERED -> {
                // First field must be less than second field
                if (fields.length >= 2) {
                    yield new Document("$expr", new Document("$lt", List.of(
                        "$" + fields[0],
                        "$" + fields[1]
                    )));
                }
                yield null;
            }
            case CONDITIONAL -> {
                // Custom condition from params
                String expr = params.get("expression");
                if (expr != null) {
                    yield Document.parse(expr);
                }
                yield null;
            }
            case REFERENTIAL_INTEGRITY -> null; // Handled by application-level check
        };

        @SuppressWarnings("unchecked")
        R typedResult = (R) result;
        return typedResult;
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
     * Generates a complete MongoDB JSON Schema validation document.
     *
     * @param fieldValidations map of field names to their validation documents
     * @return the complete $jsonSchema document
     */
    public Document generateJsonSchema(Map<String, List<Document>> fieldValidations) {
        Document properties = new Document();
        List<String> required = new ArrayList<>();

        for (Map.Entry<String, List<Document>> entry : fieldValidations.entrySet()) {
            String field = entry.getKey();
            List<Document> validations = entry.getValue();

            if (!validations.isEmpty()) {
                // Combine multiple validations for the same field
                if (validations.size() == 1) {
                    properties.append(field, validations.getFirst());
                } else {
                    properties.append(field, new Document("$and", validations));
                }
                required.add(field);
            }
        }

        return new Document("$jsonSchema", new Document()
            .append("bsonType", "object")
            .append("required", required)
            .append("properties", properties));
    }

    /**
     * Generates a validation level and action configuration.
     *
     * @param strict true for strict validation (error on violation), false for moderate (warn)
     * @return the validation level document
     */
    public Document generateValidationLevel(boolean strict) {
        return new Document()
            .append("validationLevel", strict ? "strict" : "moderate")
            .append("validationAction", strict ? "error" : "warn");
    }

    private Number parseNumber(String value) {
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

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
            case UNIQUE -> true; // Handled at database level
            case REQUIRED -> value != null && !isEmpty(value);
            case REFERENCE_EXISTS -> value != null;
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
                if (values[0] == null || isEmpty(values[0])) yield true;
                for (int i = 1; i < values.length; i++) {
                    if (values[i] == null || isEmpty(values[i])) yield false;
                }
                yield true;
            }
            case MUTUALLY_EXCLUSIVE -> {
                // Only one of the fields can be non-null
                int nonNullCount = 0;
                for (Object value : values) {
                    if (value != null && !isEmpty(value)) nonNullCount++;
                }
                yield nonNullCount <= 1;
            }
            case CONDITIONAL -> true; // Custom logic, skip here
            case REFERENTIAL_INTEGRITY -> false;
        };
    }

    private String buildErrorMessage(Validate.Rule rule, String field, Map<String, String> params) {
        return ValidationMessages.buildErrorMessage(rule, field, params);
    }
}
