package io.github.flameyossnowy.universal.microservices.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.flameyossnowy.universal.api.annotations.NetworkRepository;
import io.github.flameyossnowy.universal.api.annotations.RemoteEndpoint;
import io.github.flameyossnowy.universal.api.annotations.builder.EndpointConfig;
import io.github.flameyossnowy.universal.api.annotations.enums.AuthType;
import io.github.flameyossnowy.universal.api.annotations.enums.HttpMethod;
import io.github.flameyossnowy.universal.api.annotations.enums.NetworkProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builder for creating {@link NetworkRepositoryAdapter} instances.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class NetworkRepositoryAdapterBuilder<T, ID> {
    private final Class<T> entityType;
    private final Class<ID> idType;
    private String baseUrl;
    private NetworkProtocol protocol = NetworkProtocol.REST;
    private AuthType authType = AuthType.NONE;
    private Supplier<String> credentialsProvider;
    private int connectTimeout = 5000; // 5 seconds
    private int readTimeout = 30000;   // 30 seconds
    private int maxRetries = 3;
    private boolean cacheEnabled = false;
    private int cacheTtl = 300; // 5 minutes in seconds
    private final Map<String, String> customHeaders = new HashMap<>();
    private EndpointConfig endpointConfig = new EndpointConfig(
            "", 
            "/{id}", 
            "", 
            "/{id}", 
            "/{id}",
            HttpMethod.PUT
    );
    private ObjectMapper customObjectMapper;

    /**
     * Creates a new builder for the given entity and ID types.
     */
    public NetworkRepositoryAdapterBuilder(@NotNull Class<T> entityType, @NotNull Class<ID> idType) {
        this.entityType = entityType;
        this.idType = idType;
    }

    /**
     * Creates a new builder from a class annotated with {@code @NetworkRepository}.
     */
    public static <T, ID> NetworkRepositoryAdapterBuilder<T, ID> from(@NotNull Class<T> entityType, @NotNull Class<ID> idType) {
        NetworkRepository annotation = entityType.getAnnotation(NetworkRepository.class);
        RemoteEndpoint endpoint = entityType.getAnnotation(RemoteEndpoint.class);
        
        NetworkRepositoryAdapterBuilder<T, ID> builder = new NetworkRepositoryAdapterBuilder<>(entityType, idType)
                .baseUrl(annotation.baseUrl())
                .protocol(annotation.protocol())
                .authType(annotation.authType())
                .connectTimeout(annotation.connectTimeout())
                .readTimeout(annotation.readTimeout())
                .cacheEnabled(annotation.enableCache())
                .cacheTtl(annotation.cacheTtl())
                .maxRetries(annotation.maxRetries());
        
        if (endpoint != null) {
            builder.endpointConfig(new EndpointConfig(
                    endpoint.findAll(),
                    endpoint.findById(),
                    endpoint.create(),
                    endpoint.update(),
                    endpoint.delete(),
                    endpoint.updateMethod()
            ));
        }
        
        // Add custom headers
        for (String header : annotation.headers()) {
            String[] parts = header.split(":", 2);
            if (parts.length == 2) {
                builder.addHeader(parts[0].trim(), parts[1].trim());
            }
        }
        
        return builder;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> protocol(NetworkProtocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> authType(AuthType authType) {
        this.authType = authType;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> credentialsProvider(Supplier<String> credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> connectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> readTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> cacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> cacheTtl(int cacheTtl) {
        this.cacheTtl = cacheTtl;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> addHeader(String name, String value) {
        this.customHeaders.put(name, value);
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> endpointConfig(EndpointConfig endpointConfig) {
        this.endpointConfig = endpointConfig;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> objectMapper(ObjectMapper objectMapper) {
        this.customObjectMapper = objectMapper;
        return this;
    }

    /**
     * Builds and returns a new {@link NetworkRepositoryAdapter} instance.
     */
    public NetworkRepositoryAdapter<T, ID> build() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("baseUrl must be specified");
        }
        
        ObjectMapper objectMapper = customObjectMapper != null ? customObjectMapper : createDefaultObjectMapper();
        
        return new NetworkRepositoryAdapter<>(
                entityType,
                idType,
                baseUrl,
                protocol,
                authType,
                credentialsProvider,
                connectTimeout,
                readTimeout,
                maxRetries,
                cacheEnabled,
                cacheTtl,
                new HashMap<>(customHeaders),
                endpointConfig,
                createDefaultObjectMapper()
        );
    }
    
    public static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
