package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as binary, which means it should be stored as a binary type in the database.
 * <p>
 * This exists because some people want to store binary data in the database, and some people
 * want to store binary data in the database, for stuff like UUIDs, InetAddresses, etc.
 * @author flameyosflow
 * @version 6.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Binary {
}
