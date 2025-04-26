package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.cache.SessionCache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GlobalCacheable {
    Class<?> sessionCache() default SessionCache.class;
}
