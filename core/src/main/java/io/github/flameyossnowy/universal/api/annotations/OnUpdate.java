package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.OnModify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OnUpdate {
    OnModify value() default OnModify.CASCADE;
}