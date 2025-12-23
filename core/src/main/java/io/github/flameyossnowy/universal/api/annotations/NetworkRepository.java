package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.AuthType;
import io.github.flameyossnowy.universal.api.annotations.enums.NetworkProtocol;

import java.lang.annotation.*;

/**
 * Marks a repository as network-based (microservices, HTTP API, gRPC).
 * <p>
 * This annotation enables remote repository access over various network protocols.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @Repository(name = "user-service")
 * @NetworkRepository(
 *     baseUrl = "https://api.example.com/users",
 *     protocol = NetworkProtocol.REST,
 *     authType = AuthType.BEARER_TOKEN
 * )
 * record User(@PrimaryKey String id, String name, String email) {}
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetworkRepository {
    /**
     * The base URL of the remote service.
     */
    String baseUrl();

    /**
     * The network protocol to use.
     */
    NetworkProtocol protocol() default NetworkProtocol.REST;

    /**
     * Authentication type.
     */
    AuthType authType() default AuthType.NONE;

    /**
     * Authentication credentials provider class.
     * Must implement {@code java.util.function.Supplier<String>}
     */
    Class<?> credentialsProvider() default void.class;

    /**
     * Connection timeout in milliseconds.
     */
    int connectTimeout() default 5000;

    /**
     * Read timeout in milliseconds.
     */
    int readTimeout() default 30000;

    /**
     * Maximum number of retries for failed requests.
     */
    int maxRetries() default 3;

    /**
     * Whether to enable response caching.
     */
    boolean enableCache() default true;

    /**
     * Cache TTL in seconds. Only used if enableCache is true.
     */
    int cacheTtl() default 300;

    /**
     * Custom headers to include in all requests.
     * Format: "Header-Name: value"
     */
    String[] headers() default {};
}
