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

package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.Temporal;

/**
 * Utility class for temporal value conversions.
 *
 * @author flameyosflow
 * @version 7.2.0
 * @since 7.2.0
 */
public final class TemporalUtils {

    private TemporalUtils() {
        // Utility class
    }

    /**
     * Converts various temporal types to Instant.
     * Supports Instant, Temporal, and ISO-8601 string formats.
     *
     * @param value the value to convert
     * @return the Instant or null if cannot be parsed
     */
    @Nullable
    public static Instant parseInstant(Object value) {
        switch (value) {
            case null -> {}

            case Instant i -> {
                return i;
            }

            case Temporal t -> {
                try {
                    return Instant.from(t);
                } catch (Exception e) {
                    return null;
                }
            }
            case String s -> {
                try {
                    return Instant.parse(s);
                } catch (Exception e) {
                    return null;
                }
            }
            case java.util.Date d -> d.toInstant();

            case Long millis -> Instant.ofEpochMilli(millis);
            default -> {}
        }
        return null;
    }

    /**
     * Checks if a value is in the past.
     *
     * @param value the temporal value
     * @return true if in the past
     */
    public static boolean isInPast(Object value) {
        Instant instant = parseInstant(value);
        return instant != null && instant.isBefore(Instant.now());
    }

    /**
     * Checks if a value is in the future.
     *
     * @param value the temporal value
     * @return true if in the future
     */
    public static boolean isInFuture(Object value) {
        Instant instant = parseInstant(value);
        return instant != null && instant.isAfter(Instant.now());
    }

    /**
     * Checks if a value is in the past or present.
     *
     * @param value the temporal value
     * @return true if in the past or present
     */
    public static boolean isPastOrPresent(Object value) {
        Instant instant = parseInstant(value);
        return instant != null && !instant.isAfter(Instant.now());
    }

    /**
     * Checks if a value is in the future or present.
     *
     * @param value the temporal value
     * @return true if in the future or present
     */
    public static boolean isFutureOrPresent(Object value) {
        Instant instant = parseInstant(value);
        return instant != null && !instant.isBefore(Instant.now());
    }
}
