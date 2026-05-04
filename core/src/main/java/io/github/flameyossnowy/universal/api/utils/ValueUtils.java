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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Collection;

/**
 * Utility class for value inspection and comparison.
 *
 * @author flameyosflow
 * @version 7.2.0
 * @since 7.2.0
 */
public final class ValueUtils {

    private ValueUtils() {
        // Utility class
    }

    /**
     * Checks if a value is empty.
     * Handles null, String, Collection, and arrays.
     *
     * @param value the value to check
     * @return true if empty
     */
    @Contract(pure = true)
    public static boolean isEmpty(@Nullable Object value) {
        return switch (value) {
            case null -> true;
            case String s -> s.isEmpty();
            case Collection<?> c -> c.isEmpty();
            default -> {
                if (value.getClass().isArray()) {
                    yield Array.getLength(value) == 0;
                }
                yield false;
            }
        };
    }

    /**
     * Checks if a value is null or empty.
     * Convenience method equivalent to isEmpty.
     *
     * @param value the value to check
     * @return true if null or empty
     */
    @Contract(pure = true)
    public static boolean isNullOrEmpty(@Nullable Object value) {
        return isEmpty(value);
    }

    /**
     * Gets the length/size of a value.
     * Returns 0 for null. Handles String, Collection, and arrays.
     *
     * @param value the value
     * @return the length
     */
    @Contract(pure = true)
    public static int getLength(@Nullable Object value) {
        return switch (value) {
            case null -> 0;
            case String s -> s.length();
            case Collection<?> c -> c.size();
            default -> {
                if (value.getClass().isArray()) {
                    yield Array.getLength(value);
                }
                yield 0;
            }
        };
    }

    /**
     * Compares two comparable values.
     * Returns negative if first < second, 0 if equal, positive if first > second.
     *
     * @param first the first value
     * @param second the second value
     * @return comparison result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Contract(pure = true)
    public static int compareValues(@Nullable Object first, @Nullable Object second) {
        if (first == null && second == null) return 0;
        if (first == null) return -1;
        if (second == null) return 1;

        if (first instanceof Comparable && second instanceof Comparable) {
            try {
                return ((Comparable) first).compareTo(second);
            } catch (ClassCastException e) {
                // Fall through to string comparison
            }
        }

        // Fallback to string comparison
        return first.toString().compareTo(second.toString());
    }

    /**
     * Checks if a string is blank (null, empty, or whitespace only).
     *
     * @param s the string
     * @return true if blank
     */
    @Contract(pure = true)
    public static boolean isBlank(@Nullable String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Checks if a value is a number (Number instance or numeric string).
     *
     * @param value the value
     * @return true if numeric
     */
    @Contract(pure = true)
    public static boolean isNumeric(@Nullable Object value) {
        return switch (value) {
            case Number _ -> true;
            case String s -> ValidationUtils.isDigitsOnly(s) ||
                (!s.isEmpty() && s.charAt(0) == '-' && ValidationUtils.isDigitsOnly(s.substring(1)));
            case null, default -> false;
        };
    }

    /**
     * Converts a value to double if possible.
     *
     * @param value the value
     * @return the double value, or null
     */
    @Nullable
    @Contract(pure = true)
    public static Double toDouble(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converts a value to long if possible.
     *
     * @param value the value
     * @return the long value, or null
     */
    @Nullable
    @Contract(pure = true)
    public static Long toLong(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
