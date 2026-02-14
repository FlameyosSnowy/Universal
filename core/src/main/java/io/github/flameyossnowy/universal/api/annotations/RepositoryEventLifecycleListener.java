package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The repository event lifecycle listener for the entity.
 * <p>
 * An event lifecycle listener is used to perform any actions
 * associated with the insertion, update, or deletion of entities.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface RepositoryEventLifecycleListener {
    Class<? extends EntityLifecycleListener<?>> value();
}
