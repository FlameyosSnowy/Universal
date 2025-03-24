package io.github.flameyossnowy.universal.api.annotations.proxy;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Filter.Filters.class)
public @interface Filter {
    String value();

    String operator() default "=";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Filters {
        Filter[] value();
    }
}
