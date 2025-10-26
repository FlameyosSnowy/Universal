package io.github.flameyossnowy.universal.api.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify a custom type resolver for a field.
 * This can be used to handle custom type conversions between the application and the database.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ResolveWith {
    /**
     * The resolver class to use for this field.
     * The class must implement {@link TypeResolver}.
     */
    Class<? extends TypeResolver<?>> value();
}
