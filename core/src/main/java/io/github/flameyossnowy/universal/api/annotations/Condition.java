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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The condition for the field to be inserted into the database.
 * Used for SQL CHECK constraints.
 *
 * <p><strong>Deprecated:</strong> This annotation is SQL-biased and uses raw strings
 * which are not type-safe. Use the new {@link Validate} annotation instead which provides
 * universal, backend-agnostic validation with compile-time safety.
 *
 * <p>Migration example:
 * <pre>{@code
 * // Old (deprecated):
 * @Condition("age >= 18 AND age <= 120")
 * private int age;
 *
 * // New (recommended):
 * @Validate(Rule.RANGE)
 * @Validate.Param(name = "min", value = "18")
 * @Validate.Param(name = "max", value = "120")
 * private int age;
 * }</pre>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @deprecated since 7.2.0, use {@link Validate} with {@link Validate.Rule} instead.
 *             This annotation will be removed in a future release.
 * @see Validate
 * @see Validate.Rule
 */
@Deprecated(since = "7.2.0", forRemoval = true)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Condition {
    /**
     * The SQL condition expression.
     * Example: "age > 18"
     *
     * @return the SQL condition string
     * @deprecated use {@link Validate} with appropriate {@link Validate.Rule}
     */
    @Deprecated(since = "7.2.0", forRemoval = true)
    String value();
}
