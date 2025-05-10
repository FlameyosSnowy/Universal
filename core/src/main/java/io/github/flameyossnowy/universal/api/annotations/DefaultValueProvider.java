package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.defvalues.DefaultTypeProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Default value provider to provide a default value for a field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DefaultValueProvider {
    /**
     * The default value provider class.
     * @return The default value provider class
     */
    Class<? extends DefaultTypeProvider<?>> value();
}
