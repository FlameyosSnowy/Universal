package io.github.flameyossnowy.universal.mongodb.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MongoResolver { Class<?> value(); }