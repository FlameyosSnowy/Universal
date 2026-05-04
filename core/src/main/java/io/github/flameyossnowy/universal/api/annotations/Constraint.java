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

import java.lang.annotation.*;

/**
 * Universal, database-agnostic constraint annotation for cross-field validation.
 * Defines constraints that involve multiple fields or entity-level conditions.
 *
 * <p>Each constraint type is translated to backend-specific mechanisms:
 * <ul>
 *   <li><b>SQL</b>: CHECK constraints, compound UNIQUE indexes, triggers</li>
 *   <li><b>MongoDB</b>: Schema validation, compound indexes</li>
 *   <li><b>File-based</b>: Application-level validation</li>
 * </ul>
 *
 * <p>Example usage for cross-field constraints:
 * <pre>{@code
 * @Repository(name = "events")
 * @Constraint(value = Type.ORDERED, fields = {"startDate", "endDate"},
 *            message = "End date must be after start date")
 * @Constraint(value = Type.UNIQUE_COMBINATION, fields = {"organizerId", "eventName"},
 *            message = "Event name must be unique per organizer")
 * public class Event {
 *     private LocalDate startDate;
 *     private LocalDate endDate;
 *     private UUID organizerId;
 *     private String eventName;
 * }
 * }</pre>
 *
 * <p>Example for mutually exclusive fields:
 * <pre>{@code
 * @Constraint(value = Type.MUTUALLY_EXCLUSIVE, fields = {"email", "phone"},
 *            message = "Provide either email or phone, not both")
 * public class Contact {
 *     private String email;
 *     private String phone;
 * }
 * }</pre>
 *
 * @author flameyosflow
 * @version 7.2.0
 * @see Type
 * @see Constraints
 * @since 7.2.0
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(value = Constraints.class)
public @interface Constraint {
    /**
     * The constraint type that defines the validation semantics.
     * Each type is translated to appropriate backend-specific constraints.
     *
     * @return the constraint type
     */
    Type value();

    /**
     * Fields involved in this constraint. The interpretation depends on the constraint type:
     * <ul>
     *   <li>{@link Type#UNIQUE_COMBINATION} - all fields must be unique together</li>
     *   <li>{@link Type#REQUIRES} - if first field exists, all others must exist</li>
     *   <li>{@link Type#MUTUALLY_EXCLUSIVE} - only one of the fields can be non-null</li>
     *   <li>{@link Type#ORDERED} - first field must be less than second field</li>
     *   <li>{@link Type#CONDITIONAL} - fields involved in the condition</li>
     * </ul>
     *
     * @return the field names involved in the constraint
     */
    String[] fields() default {};

    /**
     * Optional constraint name. If not specified, a name will be generated
     * based on the constraint type and involved fields.
     *
     * @return the constraint name
     * @deprecated Use {@link #value()} and {@link #fields()} instead. Name is now auto-generated.
     */
    @Deprecated(since = "7.1.8", forRemoval = true)
    String name() default "";

    /**
     * Error message to display when the constraint is violated.
     * If not specified, a default message based on the constraint type will be used.
     *
     * @return the error message
     */
    String message() default "";

    /**
     * Cross-field and entity-level constraint types supported across all backends.
     */
    enum Type {
        /**
         * SQL: UNIQUE(a,b,c) | Mongo: compound unique index
         * All specified fields must have a unique combination of values.
         */
        UNIQUE_COMBINATION,

        /**
         * If the first field exists (is non-null), all other fields must also exist.
         * SQL: CHECK constraint | Mongo: $jsonSchema
         */
        REQUIRES,

        /**
         * Only one of the specified fields can be non-null at a time.
         * Useful for "either/or" scenarios.
         * SQL: CHECK constraint | Mongo: $jsonSchema with oneOf
         */
        MUTUALLY_EXCLUSIVE,

        /**
         * First field must be less than second field (e.g., startDate < endDate).
         * SQL: CHECK constraint | Mongo: $jsonSchema
         */
        ORDERED,

        /**
         * Custom conditional constraint. Implementation depends on the backend.
         * SQL: CHECK with custom expression | Mongo: custom validation
         */
        CONDITIONAL,

        /**
         * Foreign key / referential integrity check.
         * SQL: FOREIGN KEY constraint | Mongo: application-level check
         */
        REFERENTIAL_INTEGRITY
    }
}