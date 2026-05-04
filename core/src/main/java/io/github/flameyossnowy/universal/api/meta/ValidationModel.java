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

package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.annotations.Validate;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a parsed {@link Validate} annotation with all its rules and parameters.
 * This model is used at compile-time to generate backend-specific validation code.
 *
 * <p>Each ValidationModel contains:
 * <ul>
 *   <li>The validation rules to apply</li>
 *   <li>Parameters for parameterized rules (min, max, pattern, etc.)</li>
 *   <li>Optional custom validator class</li>
 *   <li>Custom error message</li>
 * </ul>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @see Validate
 * @see Validate.Rule
 * @since 2.0.0
 */
public final class ValidationModel {
    private final Validate.Rule[] rules;
    private final Map<String, String> params;
    private final String customValidatorClass;
    private final String message;

    /**
     * Creates a new ValidationModel.
     *
     * @param rules the validation rules
     * @param params the rule parameters
     * @param customValidatorClass the custom validator class name, or null
     * @param message the custom error message, or null
     */
    public ValidationModel(Validate.Rule[] rules,
                           Map<String, String> params,
                           String customValidatorClass,
                           String message) {
        this.rules = rules != null ? rules : new Validate.Rule[0];
        this.params = params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
        this.customValidatorClass = customValidatorClass;
        this.message = message;
    }

    /**
     * Returns the validation rules.
     *
     * @return the rules array (never null)
     */
    public Validate.Rule[] rules() {
        return rules.clone();
    }

    /**
     * Returns the rule parameters.
     *
     * @return an unmodifiable map of parameter names to values
     */
    public Map<String, String> params() {
        return params;
    }

    /**
     * Returns a specific parameter value.
     *
     * @param name the parameter name
     * @return the parameter value, or null if not present
     */
    public String param(String name) {
        return params.get(name);
    }

    /**
     * Checks if a parameter exists.
     *
     * @param name the parameter name
     * @return true if the parameter exists
     */
    public boolean hasParam(String name) {
        return params.containsKey(name);
    }

    /**
     * Returns the custom validator class name.
     *
     * @return the class name, or null if no custom validator
     */
    public String customValidatorClass() {
        return customValidatorClass;
    }

    /**
     * Checks if a custom validator is specified.
     *
     * @return true if a custom validator is present
     */
    public boolean hasCustomValidator() {
        return customValidatorClass != null && !customValidatorClass.isEmpty();
    }

    /**
     * Returns the custom error message.
     *
     * @return the message, or null if not specified
     */
    public String message() {
        return message;
    }

    /**
     * Checks if this validation has any rules or custom validator.
     *
     * @return true if this model contains validation
     */
    public boolean hasValidation() {
        return rules.length > 0 || hasCustomValidator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationModel that = (ValidationModel) o;
        return Objects.deepEquals(rules, that.rules) &&
               Objects.equals(params, that.params) &&
               Objects.equals(customValidatorClass, that.customValidatorClass) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(java.util.Arrays.hashCode(rules), params, customValidatorClass, message);
    }

    @Override
    public String toString() {
        return "ValidationModel{" +
               "rules=" + java.util.Arrays.toString(rules) +
               ", params=" + params +
               ", customValidatorClass='" + customValidatorClass + '\'' +
               ", message='" + message + '\'' +
               '}';
    }
}
