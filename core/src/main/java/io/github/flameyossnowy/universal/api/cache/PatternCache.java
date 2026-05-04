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

package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.velocis.cache.algorithms.ConcurrentLRUCache;

import java.util.regex.Pattern;

/**
 * Shared concurrent LRU cache for compiled regex patterns across all validators.
 * <p>
 * This cache improves validation performance by avoiding repeated compilation
 * of the same regex patterns. It uses a static ConcurrentLRUCache with a
 * default capacity of 256 entries, which is shared across all validation
 * translators in the system.
 *
 * <p>Usage example:
 * <pre>{@code
 * Pattern pattern = PatternCache.getOrCompile("[a-z]+");
 * boolean matches = pattern.matcher(input).matches();
 * }</pre>
 *
 * @author flameyosflow
 * @since 7.2.0
 */
public final class PatternCache {
    private static final int DEFAULT_CAPACITY = 256;
    private static final ConcurrentLRUCache<String, Pattern> CACHE = new ConcurrentLRUCache<>(DEFAULT_CAPACITY);

    private PatternCache() {
        // Utility class - prevent instantiation
    }

    /**
     * Retrieves a compiled Pattern from the cache, or compiles and caches it if not present.
     *
     * @param regex the regular expression pattern string
     * @return the compiled Pattern instance
     * @throws java.util.regex.PatternSyntaxException if the regex is invalid
     */
    public static Pattern getOrCompile(String regex) {
        return CACHE.computeIfAbsent(regex, Pattern::compile);
    }

    /**
     * Retrieves a compiled Pattern from the cache with specified flags,
     * or compiles and caches it if not present.
     *
     * @param regex the regular expression pattern string
     * @param flags the match flags (e.g., CASE_INSENSITIVE, MULTILINE)
     * @return the compiled Pattern instance with the specified flags
     * @throws java.util.regex.PatternSyntaxException if the regex is invalid
     * @throws IllegalArgumentException if the flags contain invalid bits
     */
    public static Pattern getOrCompile(String regex, int flags) {
        String key = regex + "|" + flags;
        return CACHE.computeIfAbsent(key, k -> Pattern.compile(regex, flags));
    }

    /**
     * Clears all cached patterns.
     * <p>
     * This method should be used sparingly, typically only during testing
     * or when memory needs to be reclaimed.
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Returns the current size of the cache.
     *
     * @return the number of compiled patterns currently cached
     */
    public static int size() {
        return CACHE.size();
    }

    /**
     * Checks if a pattern is currently cached.
     *
     * @param regex the regular expression pattern string
     * @return true if the pattern is in the cache, false otherwise
     */
    public static boolean contains(String regex) {
        return CACHE.containsKey(regex);
    }
}
