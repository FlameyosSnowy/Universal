package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Constraints {
    Constraint[] value();
}
