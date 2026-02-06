package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

/**
 * Define a specific constraint for a single field or combination of fields.
 * @author flameyosflow
 * @version 2.0.0
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(value = Constraints.class)
public @interface Constraint {
    String name();

    String[] fields() default {};
}