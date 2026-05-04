package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

/**
 * Define specific constraints for many fields.
 * @author flameyosflow
 * @version 2.0.0
 * @deprecated use {@link Validate} with appropriate {@link Validate.Rule} instead.
 *             This annotation will be removed in a future release.
 */
@Deprecated(since = "7.2.0", forRemoval = true)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Constraints {
    Constraint[] value();
}
