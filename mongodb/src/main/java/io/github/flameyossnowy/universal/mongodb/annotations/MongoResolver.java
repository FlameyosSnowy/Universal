package io.github.flameyossnowy.universal.mongodb.annotations;

import org.bson.codecs.Codec;

import java.lang.annotation.*;

/**
 * The annotation used to mark a field as a MongoDB resolver.
 * <p>
 * When applied to a field, this annotation indicates that the field is a MongoDB resolver, which can be used to resolve MongoDB queries and convert the java object to the database.
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MongoResolver {
    Class<? extends Codec<?>> value();
}