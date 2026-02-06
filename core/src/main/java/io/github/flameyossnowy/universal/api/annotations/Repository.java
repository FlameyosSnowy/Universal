package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation used to mark a class as a repository.
 * <p>
 * When applied to a class, this annotation indicates that the class is a repository, which can be used to interact with a database or other storage system.
 * <p>
 * Whether as a SQL Table, MongoDB Collection, etc.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Repository {
    /**
     * The name of the repository, which is the name of the table (SQL) or the collection (MongoDB).
     * @return the name of the repository
     */
    String name();
}
