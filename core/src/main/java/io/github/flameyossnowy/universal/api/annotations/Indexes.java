package io.github.flameyossnowy.universal.api.annotations;



import java.lang.annotation.*;

/**
 * Many indexes annotation.
 * @author flameyosflow
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Indexes {
    Index[] value();
}
