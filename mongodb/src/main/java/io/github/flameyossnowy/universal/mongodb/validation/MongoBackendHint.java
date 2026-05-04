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

import io.github.flameyossnowy.universal.api.validation.BackendHint;

/**
 * MongoDB-specific backend hints for validation optimization.
 *
 * <p>Example usage:
 * <pre>{@code
 * builder.withBackendHint(MongoBackendHint.strict())
 *        .withBackendHint(MongoBackendHint.errorOnViolation())
 * }</pre>
 *
 * @author flameyosflow
 * @version 2.0.0
 * @see BackendHint
 * @since 2.0.0
 */
public final class MongoBackendHint implements BackendHint {

    private final String hintType;
    private final String value;
    private final int priority;

    private MongoBackendHint(String hintType, String value, int priority) {
        this.hintType = hintType;
        this.value = value;
        this.priority = priority;
    }

    @Override
    public String getBackendType() {
        return "mongodb";
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
     * Use strict validation level (all inserts and updates must pass validation).
     */
    public static MongoBackendHint strict() {
        return new MongoBackendHint("validationLevel", "strict", 10);
    }

    /**
     * Use moderate validation level (only valid documents are affected, existing invalid documents allowed).
     */
    public static MongoBackendHint moderate() {
        return new MongoBackendHint("validationLevel", "moderate", 10);
    }

    /**
     * Throw an error on validation violation (prevents write).
     */
    public static MongoBackendHint errorOnViolation() {
        return new MongoBackendHint("validationAction", "error", 8);
    }

    /**
     * Only warn on validation violation (allows write with warning).
     */
    public static MongoBackendHint warnOnViolation() {
        return new MongoBackendHint("validationAction", "warn", 8);
    }

    /**
     * Use JSON Schema for validation (default).
     */
    public static MongoBackendHint useJsonSchema() {
        return new MongoBackendHint("validationType", "jsonSchema", 5);
    }

    /**
     * Use expression-based validation ($expr).
     */
    public static MongoBackendHint useExpression() {
        return new MongoBackendHint("validationType", "expression", 5);
    }

    /**
     * Create a custom MongoDB backend hint.
     *
     * @param hintType the type of hint
     * @param value the hint value
     * @param priority the hint priority
     * @return the custom hint
     */
    public static MongoBackendHint custom(String hintType, String value, int priority) {
        return new MongoBackendHint(hintType, value, priority);
    }
}
