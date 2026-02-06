package io.github.flameyossnowy.universal.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as referencing an entity from a different repository adapter.
 * <p>
 * This annotation enables cross-platform repository linking, allowing entities
 * backed by different storage systems (e.g., MySQL, MongoDB, Cassandra) to
 * reference each other.
 * <p>
 * Example:
 * <pre>{@code
 * @Repository(name = "users")
 * public class User {
 *     @Id
 *     private UUID id;
 *     
 *     @ExternalRepository(adapter = "path_entries")
 *     @OneToOne
 *     private PathEntry cachePath;
 * }
 * 
 * @Repository(name = "path_entries")
 * public record PathEntry(
 *     @Id Path entry,
 *     @OneToMany(mappedBy = Path.class) List<Path> directories,
 *     FileAttributes attributes,
 *     @OneToOne @ExternalRepository(adapter = "users") User associatedUser
 * ) {}
 * }</pre>
 * 
 * @author FlameyosFlow
 * @see Repository
 * @see OneToOne
 * @see OneToMany
 * @see ManyToOne
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface ExternalRepository {
    /**
     * The name/identifier of the external repository adapter.
     * This must match the name registered in the RepositoryRegistry.
     * 
     * @return the adapter name
     */
    String adapter();
    
    /**
     * Optional: The repository name in the external adapter.
     * If not specified, it will be derived from the field type's @Repository annotation.
     * 
     * @return the repository name
     */
    String repository() default "";
}
