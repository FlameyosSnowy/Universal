package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a field (which is a numeric ID) should be auto-incremented.
 * <p>
 * This will not work on non-numeric IDs or on MongoDB.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoIncrement {
}
