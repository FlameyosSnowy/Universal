package io.github.flameyossnowy.universal.api.annotations;



import java.lang.annotation.*;

/**
 * Container annotation for multiple @Index annotations.
 * Used for both class-level and field-level repeatable indexes.
 * @author flameyosflow
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Indexes {
    Index[] value();
}
