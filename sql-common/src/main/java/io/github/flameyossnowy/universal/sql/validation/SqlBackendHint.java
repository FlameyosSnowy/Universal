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

import io.github.flameyossnowy.universal.api.validation.BackendHint;

/**
 * SQL-specific backend hints for validation optimization.
 *
 * <p>Example usage:
 * <pre>{@code
 * builder.withBackendHint(SqlBackendHint.useTrigger())
 *        .withBackendHint(SqlBackendHint.deferrable())
 * }</pre>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @see BackendHint
 * @since 2.0.0
 */
public final class SqlBackendHint implements BackendHint {

    private final String hintType;
    private final String value;
    private final int priority;

    private SqlBackendHint(String hintType, String value, int priority) {
        this.hintType = hintType;
        this.value = value;
        this.priority = priority;
    }

    @Override
    public String getBackendType() {
        return "sql";
    }

    @Override
    public String getHintType() {
        return hintType;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * Use a database trigger instead of a CHECK constraint for validation.
     * Useful for complex validations that CHECK constraints cannot handle.
     */
    public static SqlBackendHint useTrigger() {
        return new SqlBackendHint("implementation", "trigger", 10);
    }

    /**
     * Make the constraint deferrable (can be checked at transaction end).
     */
    public static SqlBackendHint deferrable() {
        return new SqlBackendHint("timing", "deferrable", 5);
    }

    /**
     * Make the constraint immediate (checked immediately on modification).
     */
    public static SqlBackendHint immediate() {
        return new SqlBackendHint("timing", "immediate", 5);
    }

    /**
     * Add the NOT VALID option (PostgreSQL-specific: skip validation of existing data).
     */
    public static SqlBackendHint notValid() {
        return new SqlBackendHint("validation", "not_valid", 8);
    }

    /**
     * Use a domain type instead of a CHECK constraint.
     */
    public static SqlBackendHint useDomain() {
        return new SqlBackendHint("implementation", "domain", 10);
    }

    /**
     * Create a custom SQL backend hint.
     *
     * @param hintType the type of hint
     * @param value the hint value
     * @param priority the hint priority
     * @return the custom hint
     */
    public static SqlBackendHint custom(String hintType, String value, int priority) {
        return new SqlBackendHint(hintType, value, priority);
    }
}
