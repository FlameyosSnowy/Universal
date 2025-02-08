package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Cast {
    Class<?> type();

    Operation[] operations() default {};
}
