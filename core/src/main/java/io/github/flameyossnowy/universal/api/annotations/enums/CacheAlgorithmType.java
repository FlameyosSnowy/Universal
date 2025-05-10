package io.github.flameyossnowy.universal.api.annotations.enums;

/**
 * The type of cache algorithm to use.
 * <p>
 * LEAST_FREQUENTLY_USED: least frequently used
 * LEAST_RECENTLY_USED: least recently used
 * LEAST_FREQ_AND_RECENTLY_USED: least frequently used and recently used
 * NONE: no cache
 */
public enum CacheAlgorithmType {
    LEAST_FREQUENTLY_USED,
    LEAST_RECENTLY_USED,
    LEAST_FREQ_AND_RECENTLY_USED,
    NONE
}
