package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.HttpMethod;

import java.lang.annotation.*;

/**
 * Specifies custom endpoint mappings for repository operations.
 * <p>
 * This annotation allows you to customize how operations map to remote endpoints.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @Repository(name = "products")
 * @NetworkRepository(baseUrl = "https://api.store.com")
 * @RemoteEndpoint(
 *     findAll = "/v2/products",
 *     findById = "/v2/products/{id}",
 *     create = "/v2/products",
 *     update = "/v2/products/{id}",
 *     delete = "/v2/products/{id}"
 * )
 * record Product(@PrimaryKey String id, String name, double price) {}
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RemoteEndpoint {
    /**
     * Endpoint for finding all entities (GET).
     */
    String findAll() default "";

    /**
     * Endpoint for finding by ID (GET).
     * Use {id} as placeholder for the ID value.
     */
    String findById() default "/{id}";

    /**
     * Endpoint for creating entities (POST).
     */
    String create() default "";

    /**
     * Endpoint for updating entities (PUT/PATCH).
     * Use {id} as placeholder for the ID value.
     */
    String update() default "/{id}";

    /**
     * Endpoint for deleting entities (DELETE).
     * Use {id} as placeholder for the ID value.
     */
    String delete() default "/{id}";

    /**
     * HTTP method for update operations.
     */
    HttpMethod updateMethod() default HttpMethod.PUT;
}
