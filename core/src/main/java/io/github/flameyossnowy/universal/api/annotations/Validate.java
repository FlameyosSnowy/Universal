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

package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.validation.ValidationRule;

import java.lang.annotation.*;

/// Universal, database-agnostic validation annotation for entity fields.
///
/// Each validation rule is translated to backend-specific constraints:
///
///   - **SQL**: CHECK constraints
///   - **MongoDB**: Schema validation rules
///   - **File-based**: Pre-write validation
///
/// Example usage:
///
/// ```
/// @Repository(name = "users")
/// public class User {
///     @Id
///     private UUID id;
///
///     // Built-in rules (backend-agnostic)
///     @Validate({Rule.NOT_BLANK, Rule.MAX_LENGTH})
///     @Validate.Param(name = "max", value = "100")
///     private String username;
///
///     // Range validation
///     @Validate(Rule.RANGE)
///     @Validate.Param(name = "min", value = "18")
///     @Validate.Param(name = "max", value = "120")
///     private int age;
///
///     // Regex pattern
///     @Validate(Rule.PATTERN)
///     @Validate.Param(name = "pattern", value = "^[A-Z]{2}\\d{4}$")
///     private String postalCode;
///
///     // Custom validator
///     @Validate(custom = PhoneValidator.class)
///     private String phoneNumber;
/// }
/// ```
///
/// @author flameyosflow
/// @version 7.2.0
/// @see Rule
/// @see Param
/// @since 7.2.0
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
@Repeatable(value = Validations.class)
public @interface Validate {
    /**
     * Built-in validation rules that work across all backends.
     * Multiple rules can be combined.
     *
     * @return the validation rules to apply
     */
    Rule[] value() default {};

    /**
     * Custom validator class that implements {@link ValidationRule}.
     * Allows for complex, application-specific validation logic.
     *
     * @return the custom validator class, or Void.class if not specified
     */
    Class<?> custom() default Void.class;

    /**
     * Error message to display when validation fails.
     * If not specified, a default message based on the rule will be used.
     *
     * @return the error message
     */
    String message() default "";

    /**
     * Validation rules that are supported across all backends.
     * Each backend module provides its own translation to native constraints.
     */
    enum Rule {
        /** Value must not be null */
        NOT_NULL,

        /** String/Collection: non-empty (length > 0 or size > 0) */
        NOT_EMPTY,

        /** String: non-whitespace only */
        NOT_BLANK,

        /** Number > 0 */
        POSITIVE,

        /** Number >= 0 */
        POSITIVE_OR_ZERO,

        /** Number < 0 */
        NEGATIVE,

        /** Number <= 0 */
        NEGATIVE_OR_ZERO,

        /** Number: minimum value (use with @Param(name="min")) */
        MIN,

        /** Number: maximum value (use with @Param(name="max")) */
        MAX,

        /** String/Collection/Array: minimum length/size */
        MIN_LENGTH,

        /** String/Collection/Array: maximum length/size */
        MAX_LENGTH,

        /** Number: between min and max (inclusive) */
        RANGE,

        /** String: regex pattern match (use with @Param(name="pattern")) */
        PATTERN,

        /** String: email format validation */
        EMAIL,

        /** String: URL format validation */
        URL,

        /** String: UUID format validation */
        UUID,

        /** String: credit card number validation (Luhn algorithm) */
        CREDIT_CARD,

        /** Cross-record uniqueness check */
        UNIQUE,

        /** Date/Time: in the future */
        FUTURE,

        /** Date/Time: in the past */
        PAST,

        /** Date/Time: in the past or present */
        PAST_OR_PRESENT,

        /** Date/Time: in the future or present */
        FUTURE_OR_PRESENT,

        /** String: digits only (0-9) */
        DIGITS_ONLY,

        /** String: alphabetic characters only (a-z, A-Z) */
        ALPHA_ONLY,

        /** String: alphanumeric characters only (a-z, A-Z, 0-9) */
        ALPHANUMERIC,

        /** String: uppercase only */
        UPPERCASE,

        /** String: lowercase only */
        LOWERCASE,

        /** Value must not be null and not empty (combines NOT_NULL and NOT_EMPTY) */
        REQUIRED,

        /** Foreign key: referenced entity must exist */
        REFERENCE_EXISTS
    }

    /**
     * Parameter for parameterized validation rules.
     * Used to provide values for rules like RANGE, MIN_LENGTH, MAX_LENGTH, PATTERN.
     *
     * <p>Example:
     * <pre>{@code
     * @Validate(Rule.RANGE)
     * @Validate.Param(name = "min", value = "18")
     * @Validate.Param(name = "max", value = "120")
     * private int age;
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.FIELD)
    @Repeatable(value = Params.class)
    public @interface Param {
        /**
         * Parameter name. Common names:
         * <ul>
         *   <li>{@code min} - minimum value/length</li>
         *   <li>{@code max} - maximum value/length</li>
         *   <li>{@code pattern} - regex pattern</li>
         * </ul>
         *
         * @return the parameter name
         */
        String name();

        /**
         * Parameter value as a string. Will be parsed according to the context:
         * <ul>
         *   <li>Numbers: parsed as the appropriate numeric type</li>
         *   <li>Dates: parsed as ISO-8601</li>
         *   <li>Patterns: used as-is</li>
         * </ul>
         *
         * @return the parameter value
         */
        String value();
    }

    /**
     * Container for multiple parameters.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.FIELD)
    public @interface Params {
        Param[] value();
    }
}
