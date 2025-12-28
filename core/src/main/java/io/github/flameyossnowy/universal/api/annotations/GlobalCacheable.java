package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.cache.SessionCache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the class is cacheable, and it may be cached via ID.
 * <p>
 * It may work something like this:
 *
 * <pre>
 * &#64;GlobalCacheable
 * public class Person {
 *      &#64;Id
 *      private Long id;
 *
 *      ...
 * }
 *
 * // the above class will be automatically cached in memory by ID.
 * // such as: "4" -> "Person{id=4, name=':name', age=..., ...}"
 * </pre>
 * @author flameyosflow
 * @version 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GlobalCacheable {
    Class<?> sessionCache() default SessionCache.class;
}
