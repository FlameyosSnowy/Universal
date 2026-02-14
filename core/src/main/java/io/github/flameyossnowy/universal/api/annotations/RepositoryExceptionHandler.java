package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The exception handler for the repository.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface RepositoryExceptionHandler {
    Class<? extends ExceptionHandler<?, ?, ?>> value();
}
