package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Indexes.class)
public @interface Index {
    String name();

    String[] fields();

    IndexType type() default IndexType.NORMAL;
}
