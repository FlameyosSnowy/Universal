package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

/**
 * Define a specific constraint for a single field or combination of fields.
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = Constraints.class)
public @interface Constraint {
    String name();

    String[] fields() default {};
}