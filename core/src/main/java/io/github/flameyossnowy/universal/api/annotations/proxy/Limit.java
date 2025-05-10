package io.github.flameyossnowy.universal.api.annotations.proxy;

import java.lang.annotation.*;

/**
 * The limit of the select query.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Limit {
    int value();
}
