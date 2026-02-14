package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.json.DefaultJsonCodec;
import io.github.flameyossnowy.universal.api.json.JsonCodec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Serialize the specified field's object in JSON rather than finding a TypeResolver for it.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface JsonField {

    /**
     * Storage strategy.
     * COLUMN = single column (JSON / JSONB / TEXT)
     * TABLE  = normalized tables (default ORM behavior)
     */
    Storage storage() default Storage.COLUMN;

    /**
     * Column definition override (e.g. jsonb, json, text).
     * Empty means adapter default.
     */
    String columnDefinition() default "";

    /**
     * Serializer to use.
     * Defaults to platform JsonCodec.
     */
    Class<?> codec() default DefaultJsonCodec.class;

    /**
     * Whether partial updates are supported (JSON_PATCH / JSON_SET).
     */
    boolean supportsPartialUpdate() default false;

    /**
     * Whether this field can be queried (adapter-dependent).
     */
    boolean queryable() default false;

    enum Storage {
        COLUMN,
        TABLE
    }
}
