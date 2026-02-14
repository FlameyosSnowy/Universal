package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;

/**
 * Configuration for @Cacheable annotation
 */
public record CacheConfig(
    int maxSize,
    CacheAlgorithmType cacheAlgorithmType
) {
    public static CacheConfig none() {
        return new CacheConfig(-1, CacheAlgorithmType.NONE);
    }
    
    public boolean isEnabled() {
        return maxSize > -1;
    }
}