package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.listener.AuditLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RepositoryAuditLogger {
    Class<? extends AuditLogger<?>> value();
}
