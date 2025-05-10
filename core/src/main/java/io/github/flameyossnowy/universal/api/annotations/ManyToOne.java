package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Many-to-one relationship annotation.
 * <p>
 * An indication that the parent of this class is one instance which belongs to another repository which holds many instances of this class.
 * @see OneToMany
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ManyToOne {
    String join();
}
