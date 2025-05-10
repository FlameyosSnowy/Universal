package io.github.flameyossnowy.universal.api.annotations.proxy;

import io.github.flameyossnowy.universal.api.options.SortOrder;

import java.lang.annotation.*;

/**
 * What field to order by.
 * @author flameyosflow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OrderBy {
    String value();

    SortOrder order() default SortOrder.ASCENDING;
}
