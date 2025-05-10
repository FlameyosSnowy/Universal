package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.lang.annotation.*;

/**
 * The annotation used to mark a class as an index.
 * <p>
 * When applied to a class, this annotation indicates that the class is an index, which can be used to create indexes on a MongoDB collection or a SQL table.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Indexes.class)
public @interface Index {
    String name();

    String[] fields();

    IndexType type() default IndexType.NORMAL;
}
