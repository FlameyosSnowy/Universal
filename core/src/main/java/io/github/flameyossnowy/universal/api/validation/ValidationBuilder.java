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

/**
 * Type-safe builder API for programmatic field validation.
 * Provides a fluent interface for defining validation constraints without annotations.
 *
 * <p>This interface allows validation rules to be defined programmatically,
 * which is useful for:
 * <ul>
 *   <li>Dynamic validation based on runtime configuration</li>
 *   <li>Validation rules that depend on external conditions</li>
 *   <li>Building validation DSLs</li>
 *   <li>Testing and prototyping</li>
 * </ul>
 *
 * <p>All validation methods return {@code this} for method chaining.
 *
 * <p>Example usage:
 * <pre>{@code
 * RepositoryAdapter<User, UUID> adapter = RepositoryAdapter
 *     .builder(User.class, UUID.class)
 *     .withValidation("username", b -> b
 *         .notBlank()
 *         .maxLength(50))
 *     .withValidation("email", b -> b
 *         .notBlank()
 *         .matches(EMAIL_PATTERN))
 *     .withValidation("age", b -> b
 *         .range(18, 120))
 *     .build();
 * }</pre>
 *
 * @param <T> the type of the field being validated
 * @author flameyosflow
 * @version 7.2.0
 * @see ValidationRule
 * @see BackendHint
 * @since 7.2.0
 */
public interface ValidationBuilder<T> {

    /**
     * Validates that the value is not null.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> notNull();

    /**
     * Validates that the String/Collection/Array value is not empty.
     * For strings: length > 0
     * For collections/arrays: size > 0
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> notEmpty();

    /**
     * Validates that the String value is not blank (not null, not empty, not whitespace only).
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> notBlank();

    /**
     * Validates that the numeric value is positive (> 0).
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> positive();

    /**
     * Validates that the numeric value is positive or zero (>= 0).
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> positiveOrZero();

    /**
     * Validates that the numeric value is negative (< 0).
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> negative();

    /**
     * Validates that the String/Collection/Array has at least the specified minimum length/size.
     *
     * @param min the minimum length/size
     * @return this builder for chaining
     */
    ValidationBuilder<T> minLength(int min);

    /**
     * Validates that the String/Collection/Array has at most the specified maximum length/size.
     *
     * @param max the maximum length/size
     * @return this builder for chaining
     */
    ValidationBuilder<T> maxLength(int max);

    /**
     * Validates that the numeric value is within the specified range (inclusive).
     *
     * @param min the minimum value
     * @param max the maximum value
     * @return this builder for chaining
     */
    ValidationBuilder<T> range(Number min, Number max);

    /**
     * Validates that the String value matches the specified regex pattern.
     *
     * @param regex the regex pattern
     * @return this builder for chaining
     */
    ValidationBuilder<T> matches(String regex);

    /**
     * Validates that the String value is a valid email address.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> email();

    /**
     * Validates that the String value is a valid URL.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> url();

    /**
     * Validates that the value is unique across all records in the repository.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> unique();

    /**
     * Validates that a Date/Time value is in the future.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> future();

    /**
     * Validates that a Date/Time value is in the past.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> past();

    /**
     * Validates that a reference/foreign key exists in the referenced entity.
     *
     * @return this builder for chaining
     */
    ValidationBuilder<T> referenceExists();

    /**
     * Adds a custom validation rule.
     *
     * @param rule the custom validation rule
     * @return this builder for chaining
     */
    ValidationBuilder<T> custom(ValidationRule<T> rule);

    /**
     * Sets a custom error message for the validation.
     *
     * @param message the error message
     * @return this builder for chaining
     */
    ValidationBuilder<T> message(String message);

    /**
     * Adds a backend-specific hint for optimization.
     * This is optional and backends may ignore hints they don't support.
     *
     * @param hint the backend hint
     * @return this builder for chaining
     */
    ValidationBuilder<T> withBackendHint(BackendHint hint);
}
