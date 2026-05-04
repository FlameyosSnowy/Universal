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
 * Marker interface for backend-specific validation hints.
 * Implementations provide optimization hints or backend-specific configuration
 * for validation rules.
 *
 * <p>Backend hints are optional - backends may ignore hints they don't understand.
 * This allows validation rules to be portable across backends while still
 * supporting backend-specific optimizations.
 *
 * <p>Common hint implementations include:
 * <ul>
 *   <li>{@code SqlBackendHint} - hints for SQL backends (CHECK vs TRIGGER)</li>
 *   <li>{@code MongoBackendHint} - hints for MongoDB (validation level, action)</li>
 *   <li>{@code FileBackendHint} - hints for file-based storage</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // SQL-specific: use TRIGGER instead of CHECK constraint
 * builder.withBackendHint(SqlBackendHint.useTrigger())
 *
 * // MongoDB-specific: strict validation, error on violation
 * builder.withBackendHint(MongoBackendHint.strict())
 * }</pre>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @see ValidationBuilder
 * @since 2.0.0
 */
public interface BackendHint {

    /**
     * Returns the target backend type for this hint.
     * Used to route hints to the appropriate backend implementation.
     *
     * @return the backend type identifier (e.g., "sql", "mongodb", "file")
     */
    String getBackendType();

    /**
     * Returns the hint type/category.
     * Used for grouping and prioritizing hints.
     *
     * @return the hint type
     */
    String getHintType();

    /**
     * Returns the hint value as a string.
     * The interpretation depends on the hint type.
     *
     * @return the hint value
     */
    String getValue();

    /**
     * Returns the priority of this hint.
     * Higher priority hints are applied first.
     *
     * @return the priority value
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Checks if this hint applies to the given backend type.
     *
     * @param backendType the backend type to check
     * @return true if this hint applies to the given backend
     */
    default boolean appliesTo(String backendType) {
        return getBackendType().equalsIgnoreCase(backendType);
    }
}
