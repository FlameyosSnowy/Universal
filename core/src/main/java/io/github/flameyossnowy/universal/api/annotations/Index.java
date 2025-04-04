package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Index {
    String name();

    String[] fields();

    IndexType type() default IndexType.NORMAL;
}
