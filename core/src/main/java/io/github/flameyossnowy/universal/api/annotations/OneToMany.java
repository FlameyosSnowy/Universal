package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One-to-many relationship annotation.
 * <p>
 * This annotation is used to specify a one-to-many relationship between two entities, where this entity is the owner of the relationship, and can hold many references of the child.
 * @see ManyToOne
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToMany {
    /**
     * The child entity type. This is the entity type in the child entity that holds a reference to this entity.
     * @return the child entity type
     */
    Class<?> mappedBy();

    /**
     * Whether the relationship should be lazy loaded, also known as only loading when the list or the set is accessed.
     * @return true if lazy loading is enabled, false otherwise
     */
    boolean lazy() default false;
}
