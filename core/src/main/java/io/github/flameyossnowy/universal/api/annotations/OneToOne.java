package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One-to-one relationship annotation.
 * <p>
 * This annotation is used to specify a one-to-one relationship between two entities, where this entity is the owner of the relationship, and can hold one reference of the child.
 * @see ManyToOne
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToOne {
    boolean lazy() default false;
}
