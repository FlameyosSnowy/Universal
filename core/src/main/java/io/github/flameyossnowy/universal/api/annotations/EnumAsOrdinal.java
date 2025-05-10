package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.*;

/**
 * The annotation used to mark a field as an enumerated field, which may either be stored as a string or as an ordinal.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EnumAsOrdinal {
}
