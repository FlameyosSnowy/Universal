package io.github.flameyossnowy.universal.api.annotations.builder;

import io.github.flameyossnowy.universal.api.annotations.RemoteEndpoint;
import io.github.flameyossnowy.universal.api.annotations.enums.HttpMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for creating {@link RemoteEndpoint} configurations programmatically.
 * This provides a fluent API for building endpoint configurations.
 */
@SuppressWarnings("HardcodedFileSeparator") // not dealing with files :P
public class RemoteEndpointBuilder {
    private String findAll = "";
    private String findById = "/{id}";
    private String create = "";
    private String update = "/{id}";
    private String delete = "/{id}";
    private HttpMethod updateMethod = HttpMethod.PUT;

    /**
     * Creates a new builder with default values.
     */
    @Contract(" -> new")
    public static @NotNull RemoteEndpointBuilder builder() {
        return new RemoteEndpointBuilder();
    }

    /**
     * Creates a new builder initialized with values from an existing {@link RemoteEndpoint} annotation.
     */
    public static RemoteEndpointBuilder from(@NotNull RemoteEndpoint endpoint) {
        return builder()
                .findAll(endpoint.findAll())
                .findById(endpoint.findById())
                .create(endpoint.create())
                .update(endpoint.update())
                .delete(endpoint.delete())
                .updateMethod(endpoint.updateMethod());
    }

    public RemoteEndpointBuilder findAll(String findAll) {
        this.findAll = findAll != null ? findAll : "";
        return this;
    }

    public RemoteEndpointBuilder findById(String findById) {
        this.findById = findById != null ? findById : "/{id}";
        return this;
    }

    public RemoteEndpointBuilder create(String create) {
        this.create = create != null ? create : "";
        return this;
    }

    public RemoteEndpointBuilder update(String update) {
        this.update = update != null ? update : "/{id}";
        return this;
    }

    public RemoteEndpointBuilder delete(String delete) {
        this.delete = delete != null ? delete : "/{id}";
        return this;
    }

    public RemoteEndpointBuilder updateMethod(HttpMethod updateMethod) {
        this.updateMethod = updateMethod != null ? updateMethod : HttpMethod.PUT;
        return this;
    }

    /**
     * Builds and returns a new {@link EndpointConfig} instance.
     */
    public EndpointConfig build() {
        return new EndpointConfig(findAll, findById, create, update, delete, updateMethod);
    }

    /**
     * Creates a new {@link RemoteEndpoint} annotation with the current configuration.
     * Note: This creates a dynamic proxy that implements the annotation interface.
     */
    public RemoteEndpoint toAnnotation() {
        return new RemoteEndpoint() {
            @Override
            public String findAll() {
                return findAll;
            }

            @Override
            public String findById() {
                return findById;
            }

            @Override
            public String create() {
                return create;
            }

            @Override
            public String update() {
                return update;
            }

            @Override
            public String delete() {
                return delete;
            }

            @Override
            public HttpMethod updateMethod() {
                return updateMethod;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RemoteEndpoint.class;
            }
        };
    }
}
