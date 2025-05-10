package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The repository event lifecycle listener for the entity.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RepositoryEventLifecycleListener {
    Class<? extends EntityLifecycleListener<?>> value();
}
