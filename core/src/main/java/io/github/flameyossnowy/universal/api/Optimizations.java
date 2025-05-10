package io.github.flameyossnowy.universal.api;

public enum Optimizations {
    /**
     * Caches prepared statements to improve performance and minimize database calls.
     * <p>
     * Usually redundant when using the recommended settings constant.
     */
    CACHE_PREPARED_STATEMENTS,

    /**
     * Sets the recommended settings for the repository to optimize for performance.
     */
    RECOMMENDED_SETTINGS
}
