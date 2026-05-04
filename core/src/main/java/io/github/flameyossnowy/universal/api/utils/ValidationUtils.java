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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class providing common validation methods without regex.
 * These methods perform character-by-character validation for performance.
 *
 * @author flameyosflow
 * @version 7.2.0
 * @since 7.2.0
 */
public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class
    }

    /**
     * Validates an email address without regex.
     * Checks for @ position, dot position, and consecutive dots.
     *
     * @param email the email to validate
     * @return true if valid email format
     */
    @Contract(pure = true)
    public static boolean isValidEmail(@Nullable String email) {
        if (email == null || email.isEmpty()) return false;
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex >= email.length() - 1) return false;
        int dotIndex = email.lastIndexOf('.');
        if (dotIndex <= atIndex + 1 || dotIndex >= email.length() - 1) return false;
        // Check no consecutive dots
        for (int i = 0; i < email.length() - 1; i++) {
            if (email.charAt(i) == '.' && email.charAt(i + 1) == '.') return false;
        }
        return true;
    }

    /**
     * Validates a URL without regex.
     * Checks for http:// or https:// scheme, valid host, optional port.
     *
     * @param url the URL to validate
     * @return true if valid URL format
     */
    @Contract(pure = true)
    public static boolean isValidUrl(@Nullable String url) {
        if (url == null) return false;

        int len = url.length();
        if (len < 7) return false; // "http://"

        int i = 0;

        // ---- scheme ----
        if (len >= 8 &&
            url.charAt(0) == 'h' &&
            url.charAt(1) == 't' &&
            url.charAt(2) == 't' &&
            url.charAt(3) == 'p') {

            if (url.charAt(4) == 's') {
                if (url.charAt(5) != ':' || url.charAt(6) != '/' || url.charAt(7) != '/') return false;
                i = 8;
            } else {
                if (url.charAt(4) != ':' || url.charAt(5) != '/' || url.charAt(6) != '/') return false;
                i = 7;
            }
        } else {
            return false;
        }

        // ---- host ----
        int hostStart = i;
        boolean hasDot = false;

        for (; i < len; i++) {
            char c = url.charAt(i);

            if (c == '.') {
                hasDot = true;
                continue;
            }

            if (c == ':' || c == '/') break;

            // allow a-z A-Z 0-9 -
            if (!(c >= 'a' && c <= 'z') &&
                !(c >= 'A' && c <= 'Z') &&
                !(c >= '0' && c <= '9') &&
                c != '-') {
                return false;
            }
        }

        // host must exist and not start/end badly
        if (i == hostStart) return false;
        if (url.charAt(hostStart) == '.' || url.charAt(i - 1) == '.') return false;

        // Require dot (reject "localhost" for stricter URLs)
        if (!hasDot) return false;

        // ---- port ----
        if (i < len && url.charAt(i) == ':') {
            i++;
            int portStart = i;

            if (i >= len) return false;

            int port = 0;
            for (; i < len; i++) {
                char c = url.charAt(i);
                if (c == '/') break;

                if (c < '0' || c > '9') return false;

                port = port * 10 + (c - '0');
                if (port > 65535) return false;
            }

            if (i == portStart) return false; // empty port
        }

        // ---- path ----
        if (i < len && url.charAt(i) == '/') {
            // allow anything after '/', no need to validate deeply
            return true;
        }

        // allow URLs ending right after host or port
        return i == len;
    }

    /**
     * Checks if string contains only digits.
     *
     * @param s the string to check
     * @return true if only digits
     */
    @Contract(pure = true)
    public static boolean isDigitsOnly(@Nullable String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * Checks if string contains only letters (a-z, A-Z).
     *
     * @param s the string to check
     * @return true if only letters
     */
    @Contract(pure = true)
    public static boolean isAlphaOnly(@Nullable String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return true;
    }

    /**
     * Checks if string contains only letters and numbers.
     *
     * @param s the string to check
     * @return true if alphanumeric
     */
    @Contract(pure = true)
    public static boolean isAlphanumeric(@Nullable String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    /**
     * Validates a credit card number using the Luhn algorithm.
     * Removes spaces and dashes before validation.
     *
     * @param number the credit card number
     * @return true if valid
     */
    @Contract(pure = true)
    public static boolean isValidCreditCard(@Nullable String number) {
        if (number == null || number.isEmpty()) return false;

        // Remove spaces and dashes manually
        StringBuilder cleanBuilder = new StringBuilder(16);
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c != ' ' && c != '-' && c != '\t') {
                cleanBuilder.append(c);
            }
        }
        String clean = cleanBuilder.toString();

        if (!isDigitsOnly(clean)) return false;
        return luhnCheck(clean);
    }

    /**
     * Performs the Luhn algorithm check on a cleaned digit string.
     *
     * @param clean the cleaned digit string
     * @return true if passes Luhn check
     */
    @Contract(pure = true)
    public static boolean luhnCheck(@NotNull String clean) {
        int sum = 0;
        boolean alternate = false;
        for (int i = clean.length() - 1; i >= 0; i--) {
            int n = clean.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    /**
     * Strips whitespace and dash characters from a string.
     *
     * @param s the input string
     * @return cleaned string
     */
    @NotNull
    @Contract(pure = true)
    public static String stripSeparators(@Nullable String s) {
        if (s == null) return "";
        StringBuilder result = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '-' && c != '\t') {
                result.append(c);
            }
        }
        return result.toString();
    }
}
