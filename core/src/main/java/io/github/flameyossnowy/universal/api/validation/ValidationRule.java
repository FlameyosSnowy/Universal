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
 * Interface for custom validation rules.
 * Implement this interface to create application-specific validation logic.
 *
 * <p>Custom validators can be used with:
 * <ul>
 *   <li>Annotation-based validation: {@code @Validate(custom = MyValidator.class)}</li>
 *   <li>Builder-based validation: {@code builder.custom(myValidationRule)}</li>
 * </ul>
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class PhoneValidator implements ValidationRule<String> {
 *     private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
 *
 *     @Override
 *     public boolean validate(String value) {
 *         if (value == null || value.isBlank()) {
 *             return true; // null/blank handled by @NonNull or notEmpty
 *         }
 *         return PHONE_PATTERN.matcher(value).matches();
 *     }
 *
 *     @Override
 *     public String getErrorMessage() {
 *         return "Invalid phone number format";
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "PhoneValidator";
 *     }
 * }
 * }</pre>
 *
 * <p>Usage:
 * <pre>{@code
 * public class User {
 *     @Validate(custom = PhoneValidator.class)
 *     private String phoneNumber;
 * }
 * }</pre>
 *
 * @param <T> the type of the value to validate
 * @author flameyosflow
 * @version 2.0.0
 * @see ValidationBuilder
 * @since 2.0.0
 */
@FunctionalInterface
public interface ValidationRule<T> {

    /**
     * Validates the given value.
     *
     * @param value the value to validate, may be null
     * @return true if the value is valid, false otherwise
     */
    boolean validate(T value);

    /**
     * Returns the error message to display when validation fails.
     * Default implementation returns a generic message.
     *
     * @return the error message
     */
    default String getErrorMessage() {
        return "Validation failed for " + getName();
    }

    /**
     * Returns the name of this validation rule.
     * Used for identification and logging purposes.
     *
     * @return the validation rule name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Returns the priority of this validation rule.
     * Higher priority rules are executed first.
     * Default is 0 (normal priority).
     *
     * @return the priority value
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Checks if this validation rule supports the given value type.
     * Default implementation accepts any non-null type.
     *
     * @param type the type to check
     * @return true if this rule can validate the given type
     */
    default boolean supports(Class<?> type) {
        return true;
    }
}
