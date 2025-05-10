package io.github.flameyossnowy.universal.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation used to mark a class as a SQL resolver.
 * <p>
 * When applied to a class, this annotation indicates that the class is a SQL resolver, which can be used to resolve SQL queries and convert the java object to the database.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SQLResolver {
    Class<?> value();
}
