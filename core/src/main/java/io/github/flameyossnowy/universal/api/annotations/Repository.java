package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Repository {
    /**
     * The name of the repository, which is the name of the table (SQL) or the collection (MongoDB).
     * @return the name of the repository
     */
    String name();
}
