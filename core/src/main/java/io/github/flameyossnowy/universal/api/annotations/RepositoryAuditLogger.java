package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.listener.AuditLogger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation used to mark a class as something that contains an audit logger
 * @see AuditLogger
 * @author FlameyosFlow
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface RepositoryAuditLogger {
    Class<? extends AuditLogger<?>> value();
}
