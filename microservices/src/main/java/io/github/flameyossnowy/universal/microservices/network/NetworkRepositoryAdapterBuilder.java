package io.github.flameyossnowy.universal.microservices.network;

import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.annotations.NetworkRepository;
import io.github.flameyossnowy.universal.api.annotations.RemoteEndpoint;
import io.github.flameyossnowy.universal.api.annotations.builder.EndpointConfig;
import io.github.flameyossnowy.universal.api.annotations.enums.AuthType;
import io.github.flameyossnowy.universal.api.annotations.enums.HttpMethod;
import io.github.flameyossnowy.universal.api.annotations.enums.NetworkProtocol;
import io.github.flameyossnowy.universal.api.resolver.TypeRegistration;
import io.github.flameyossnowy.universal.api.resolver.internal.DefaultTypeRegistry;
import io.github.flameyossnowy.uniform.json.JsonAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builder for creating {@link NetworkRepositoryAdapter} instances.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
@SuppressWarnings({ "unused", "UnusedReturnValue" })
public class NetworkRepositoryAdapterBuilder<T, ID> {
    static {
        ModelsBootstrap.init();
    }

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
    private final Map<String, String> customHeaders = new HashMap<>(8);
    private EndpointConfig endpointConfig = new EndpointConfig(
            "", 
            "/{id}", 
            "", 
            "/{id}", 
            "/{id}",
            HttpMethod.PUT
    );
    private JsonAdapter customObjectMapper;
    private NetworkRepositoryAdapter.NetworkAggregationProviderFactory<T, ID> aggregationProviderFactory;
    private final List<TypeRegistration> typeRegistrations = new ArrayList<>();

    private boolean autoCreate = true;

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

    public NetworkRepositoryAdapterBuilder<T, ID> objectMapper(JsonAdapter objectMapper) {
        this.customObjectMapper = objectMapper;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> aggregationProvider(
        NetworkRepositoryAdapter.NetworkAggregationProviderFactory<T, ID> aggregationProviderFactory
    ) {
        this.aggregationProviderFactory = aggregationProviderFactory;
        return this;
    }

    public NetworkRepositoryAdapterBuilder<T, ID> setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    /**
     * Registers custom types with the repository adapter.
     *
     * <p>This method allows you to register custom type mappings, resolvers, and enums
     * that will be applied to the repository's TypeResolverRegistry during initialization.
     * Multiple registrations can be added and will be applied in order.</p>
     *
     * @param registration the type registration callback
     * @return this builder for chaining
     */
    public NetworkRepositoryAdapterBuilder<T, ID> registerTypes(TypeRegistration registration) {
        this.typeRegistrations.add(registration);
        return this;
    }

    private TypeRegistration combineRegistrations() {
        return DefaultTypeRegistry.combineRegistrations(typeRegistrations);
    }

    /**
     * Builds and returns a new {@link NetworkRepositoryAdapter} instance.
     */
    public NetworkRepositoryAdapter<T, ID> build() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("baseUrl must be specified");
        }

        JsonAdapter objectMapper = customObjectMapper != null ? customObjectMapper : createDefaultObjectMapper();

        TypeRegistration combinedRegistration = combineRegistrations();

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
                objectMapper,
                aggregationProviderFactory,
                autoCreate,
                combinedRegistration
        );
    }
    
    public static JsonAdapter createDefaultObjectMapper() {
        return new JsonAdapter(JsonAdapter.configBuilder()
            .build());
    }
}
