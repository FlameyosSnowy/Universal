package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The primary key of an entity, a good entity should usually have one id unless it is a join table (only for such as {@link ManyToOne}).
 * <p>
 * Many annotations depend on this, such as {@link OneToMany}, {@link OneToOne}, {@link AutoIncrement} and {@link GlobalCacheable}.
 * <p>
 * Example:
 * <pre>
 * public class User {
 *     &#64;Id
 *     &#64;AutoIncrement
 *     private Long id;
 *
 *     private String username;
 *
 *     public User() {}
 *
 *     public String getUsername() {
 *          return username;
 *      }
 *
 *     public Long getId() {
 *         return id;
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Id {

}
