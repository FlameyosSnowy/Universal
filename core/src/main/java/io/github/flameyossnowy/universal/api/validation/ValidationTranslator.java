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
import io.github.flameyossnowy.universal.api.factory.ObjectModel;

import java.util.List;
import java.util.Map;

/**
 * Core interface for translating universal validation rules to backend-specific formats.
 * Each backend module implements this interface to convert semantic validation rules
 * to native constraints.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Converting {@link Validate.Rule} to backend-specific validation</li>
 *   <li>Handling parameterized rules (min, max, pattern, etc.)</li>
 *   <li>Translating cross-field {@link Constraint.Type} constraints</li>
 *   <li>Applying {@link BackendHint} optimizations</li>
 * </ul>
 *
 * <p>Example SQL implementation:
 * <pre>{@code
 * public class SqlValidationTranslator implements ValidationTranslator {
 *     public String translate(Rule rule, String field, Map<String, String> params) {
 *         return switch (rule) {
 *             case NOT_EMPTY -> "LENGTH(" + field + ") > 0";
 *             case RANGE -> field + " BETWEEN " + params.get("min") + " AND " + params.get("max");
 *             case PATTERN -> field + " REGEXP '" + params.get("pattern") + "'";
 *             // ... etc
 *         };
 *     }
 *
 *     public String translateConstraint(Constraint.Type type, String[] fields, Map<String, String> params) {
 *         return switch (type) {
 *             case ORDERED -> fields[0] + " < " + fields[1];
 *             case UNIQUE_COMBINATION -> "UNIQUE(" + String.join(",", fields) + ")";
 *             // ... etc
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p>Example MongoDB implementation:
 * <pre>{@code
 * public class MongoValidationTranslator implements ValidationTranslator {
 *     public Document translate(Rule rule, String field, Map<String, String> params) {
 *         return switch (rule) {
 *             case NOT_EMPTY -> new Document(field,
 *                 new Document("$exists", true).append("$ne", ""));
 *             case RANGE -> new Document(field,
 *                 new Document("$gte", params.get("min")).append("$lte", params.get("max")));
 *             // ... etc
 *         };
 *     }
 * }
 * }</pre>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @see Validate.Rule
 * @see Constraint.Type
 * @since 2.0.0
 */
public interface ValidationTranslator<T> {

    /**
     * Translates a single validation rule to the backend-specific format.
     *
     * @param <R> the return type, specific to the backend
     * @param rule the universal validation rule
     * @param field the field name being validated
     * @param params the rule parameters (e.g., min, max, pattern)
     * @param hints optional backend hints for optimization
     * @return the backend-specific validation representation
     */
    <R> R translate(Validate.Rule rule, String field, Map<String, String> params, List<BackendHint> hints);

    /**
     * Translates a cross-field constraint to the backend-specific format.
     *
     * @param <R> the return type, specific to the backend
     * @param type the constraint type
     * @param fields the field names involved in the constraint
     * @param params optional constraint parameters
     * @param hints optional backend hints for optimization
     * @return the backend-specific constraint representation
     */
    <R> R translateConstraint(Constraint.Type type, String[] fields, Map<String, String> params, List<BackendHint> hints);

    /**
     * Checks if this translator supports the given validation rule.
     *
     * @param rule the rule to check
     * @return true if this translator can handle the rule
     */
    default boolean supports(Validate.Rule rule) {
        return true;
    }

    /**
     * Checks if this translator supports the given constraint type.
     *
     * @param type the constraint type to check
     * @return true if this translator can handle the constraint
     */
    default boolean supports(Constraint.Type type) {
        return true;
    }

    /**
     * Returns the backend type identifier for this translator.
     *
     * @return the backend type (e.g., "sql", "mongodb", "file")
     */
    String getBackendType();

    /**
     * Combines multiple validation rules into a single backend constraint.
     * Default implementation returns null, indicating no combination is needed.
     *
     * @param <R> the return type
     * @param rules the rules to combine
     * @param field the field name
     * @return the combined constraint, or null to handle rules individually
     */
    default <R> R combineRules(List<Validate.Rule> rules, String field) {
        return null;
    }

    /**
     * Validates an entity against all field-level validations and cross-field constraints.
     * Returns a list of validation violations, or an empty list if validation passes.
     *
     * @param entity the entity to validate
     * @param repositoryModel the repository model containing validation metadata
     * @param objectModel the object model for accessing field values without reflection
     * @param <ID> the ID type
     * @return list of validation violations, empty if valid
     */
    <ID> List<ValidationException.Violation> validate(T entity, io.github.flameyossnowy.universal.api.meta.RepositoryModel<T, ID> repositoryModel, ObjectModel<T, ID> objectModel);
}
