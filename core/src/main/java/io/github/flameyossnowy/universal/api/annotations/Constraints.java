package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

/**
 * Define specific constraints for many fields.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Constraints {
    Constraint[] value();
}
