package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the class is cacheable and it may be cached via queries.
 * <p>
 * It may work something like this:
 *
 * <pre>
 * &#64;Cacheable
 * public class Person {
 *      ...
 * }
 *
 * // the above class will be automatically cached in memory by queries.
 * // such as: "SELECT * FROM person WHERE name = :name" -> "Person{name=':name', age=..., ...}"
 * </pre>
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Cacheable {
    int maxCacheSize() default 100;

    CacheAlgorithmType algorithm() default CacheAlgorithmType.LEAST_FREQUENTLY_USED;
}
