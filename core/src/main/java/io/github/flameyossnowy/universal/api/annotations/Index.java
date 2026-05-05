package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.lang.annotation.*;

/**
 * The annotation used to mark a class as an index or a field as indexed.
 * <p>
 * When applied to a class, this annotation indicates that the class is an index, which can be used to create indexes on a MongoDB collection, or an SQL table or a File repository
 * <p>
 * When applied to a field, this annotation marks the field as indexed, creating an index on that single field.
 * The index name is automatically generated from the field name and repository name.
 * <p>
 * Doesn't do anything for NetworkRepositoryAdapter
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD})
@Repeatable(Indexes.class)
public @interface Index {
    /**
     * The index name. Optional for field-level indexes - defaults to "idx_{tableName}_{fieldName}".
     * Required for class-level indexes.
     */
    String name() default "";

    /**
     * The fields included in this index. Required for class-level indexes.
     * Not used for field-level indexes (the annotated field is used automatically).
     */
    String[] fields() default {};

    /**
     * The index type (NORMAL or UNIQUE).
     */
    IndexType type() default IndexType.NORMAL;
}
