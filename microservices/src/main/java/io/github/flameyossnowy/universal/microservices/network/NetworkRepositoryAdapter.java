package io.github.flameyossnowy.universal.microservices.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flameyossnowy.universal.api.CloseableIterator;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.annotations.NetworkRepository;
import io.github.flameyossnowy.universal.api.annotations.RemoteEndpoint;
import io.github.flameyossnowy.universal.api.annotations.builder.EndpointConfig;
import io.github.flameyossnowy.universal.api.annotations.enums.AuthType;
import io.github.flameyossnowy.universal.api.annotations.enums.NetworkProtocol;
import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.SortOption;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.microservices.relationship.MicroserviceRelationshipHandler;
import io.github.flameyossnowy.universal.microservices.relationship.RelationshipResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Network-based repository adapter for microservices communication.
 * <p>
 * Supports REST only for now.
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class NetworkRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, HttpClient> {
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final Class<T> entityType;
    private final Class<ID> idType;
    private final RepositoryInformation repositoryInformation;
    private final TypeResolverRegistry resolverRegistry;
    private final OperationExecutor<HttpClient> operationExecutor;
    private final OperationContext<HttpClient> operationContext;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final NetworkProtocol protocol;
    private final AuthType authType;
    private final Supplier<String> credentialsProvider;
    private final Map<String, String> customHeaders;
    private final EndpointConfig endpointConfig;
    private final RelationshipResolver<T, ID> relationshipResolver;
    
    // Response cache
    private final Map<String, CompletableFuture<CachedResponse>> responseCache;
    private final boolean cacheEnabled;
    private final long cacheTtlMillis;

    public NetworkRepositoryAdapter(
            @NotNull Class<T> entityType,
            @NotNull Class<ID> idType,
            @NotNull String baseUrl,
            NetworkProtocol protocol,
            AuthType authType,
            Supplier<String> credentialsProvider,
            int connectTimeout,
            int readTimeout,
            int maxRetries,
            boolean cacheEnabled,
            int cacheTtl,
            Map<String, String> customHeaders,
            EndpointConfig endpointConfig,
            ObjectMapper objectMapper) {
        this.entityType = entityType;
        this.idType = idType;
        this.baseUrl = baseUrl.endsWith(FileSystems.getDefault().getSeparator()) ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.protocol = protocol;
        this.authType = authType;
        this.credentialsProvider = credentialsProvider;
        this.customHeaders = customHeaders;
        this.endpointConfig = endpointConfig;
        this.cacheEnabled = cacheEnabled;
        this.cacheTtlMillis = cacheTtl * 1000L;

        this.repositoryInformation = RepositoryMetadata.getMetadata(entityType);
        if (repositoryInformation == null)
            throw new IllegalArgumentException("Could not find repository information for class: " + entityType.getSimpleName());
        this.resolverRegistry = new TypeResolverRegistry();
        
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();

        this.operationExecutor = new NetworkOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(
                repositoryInformation,
                resolverRegistry,
                operationExecutor
        );

        this.responseCache = cacheEnabled ? new ConcurrentHashMap<>(3) : null;

        this.relationshipResolver = new RelationshipResolver<>(new MicroserviceRelationshipHandler<>(repositoryInformation, idType, resolverRegistry));
        RepositoryRegistry.register(this.repositoryInformation.getRepositoryName(), this);
    }

    public static <T, ID> NetworkRepositoryAdapter<T, ID> from(
            @NotNull Class<T> entityType,
            @NotNull Class<ID> idType) {
        NetworkRepository annotation = entityType.getAnnotation(NetworkRepository.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Entity " + entityType.getName() + " must be annotated with @NetworkRepository");
        }

        Map<String, String> headers = new HashMap<>(annotation.headers().length);
        for (String header : annotation.headers()) {
            String[] parts = header.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }

        Supplier<String> credentialsProvider = null;
        if (annotation.credentialsProvider() != void.class) {
            try {
                @SuppressWarnings("unchecked")
                Supplier<String> provider = (Supplier<String>) annotation.credentialsProvider()
                        .getDeclaredConstructor()
                        .newInstance();
                credentialsProvider = provider;
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate credentials provider", e);
            }
        }

        EndpointConfig endpointConfig = getEndpointConfig(entityType);

        return new NetworkRepositoryAdapter<>(
                entityType,
                idType,
                annotation.baseUrl(),
                annotation.protocol(),
                annotation.authType(),
                credentialsProvider,
                annotation.connectTimeout(),
                annotation.readTimeout(),
                annotation.maxRetries(),
                annotation.enableCache(),
                annotation.cacheTtl(),
                headers,
                endpointConfig,
                NetworkRepositoryAdapterBuilder.createDefaultObjectMapper()
        );
    }

    private static <T> @NotNull EndpointConfig getEndpointConfig(@NotNull Class<T> entityType) {
        RemoteEndpoint remoteEndpoint = entityType.getAnnotation(RemoteEndpoint.class);
        return remoteEndpoint != null
                ? new EndpointConfig(
                    remoteEndpoint.findAll(),
                    remoteEndpoint.findById(),
                    remoteEndpoint.create(),
                    remoteEndpoint.update(),
                    remoteEndpoint.delete(),
                    remoteEndpoint.updateMethod()
                )
                : EndpointConfig.defaults();
    }

    public static <T, ID> NetworkRepositoryAdapterBuilder<T, ID> builder(@NotNull Class<T> entityType, @NotNull Class<ID> idType) {
        return new NetworkRepositoryAdapterBuilder<>(entityType, idType);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> execute(@NotNull Operation<R, HttpClient> operation) {
        return executeOperation(operation);
    }

    @Override
    @NotNull
    public <R> TransactionResult<R> execute(
            @NotNull Operation<R, HttpClient> operation,
            @NotNull TransactionContext<HttpClient> transactionContext) {
        return operation.executeWithTransaction(operationContext, transactionContext);
    }

    @Override
    @NotNull
    public OperationContext<HttpClient> getOperationContext() {
        return operationContext;
    }

    @Override
    @NotNull
    public OperationExecutor<HttpClient> getOperationExecutor() {
        return operationExecutor;
    }

    @Override
    @NotNull
    public RepositoryInformation getRepositoryInformation() {
        return repositoryInformation;
    }

    @Override
    @NotNull
    public TypeResolverRegistry getTypeResolverRegistry() {
        return resolverRegistry;
    }

    @Override
    @NotNull
    public Class<T> getEntityType() {
        return entityType;
    }

    @Override
    @NotNull
    public Class<ID> getIdType() {
        return idType;
    }

    @Override
    @NotNull
    public TransactionContext<HttpClient> beginTransaction() {
        return new NetworkTransactionContext(httpClient);
    }

    @Override
    public @NotNull List<ID> findIds(SelectQuery query) {
        List<T> entities = find(query);
        List<ID> ids = new ArrayList<>(entities.size());
        for (T entity : entities) {
            ids.add(extractId(entity));
        }
        return ids;
    }

    @Override
    public void close() {
        if (responseCache != null) {
            responseCache.clear();
        }
        httpClient.close();
        RepositoryRegistry.unregister(repositoryInformation.getRepositoryName());
    }

    public HttpRequest.Builder createRequestBuilder(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint));

        if (authType != AuthType.NONE && credentialsProvider != null) {
            String credentials = credentialsProvider.get();
            switch (authType) {
                case BEARER_TOKEN -> builder.header("Authorization", "Bearer " + credentials);
                case API_KEY -> builder.header("X-API-Key", credentials);
                case BASIC -> builder.header("Authorization", "Basic " + credentials);
                case OAUTH2, CUSTOM -> builder.header("Authorization", credentials);
            }
        }

        customHeaders.forEach(builder::header);

        builder.header("Content-Type", "application/json");
        builder.header("Accept", "application/json");

        return builder;
    }

    public <R> R sendRequest(HttpRequest request, Class<R> responseType) throws IOException, InterruptedException {
        if (cacheEnabled && request.method().equals("GET")) {
            String cacheKey = request.uri().toString();
            CompletableFuture<CachedResponse> future = responseCache.get(cacheKey);

            if (future != null) {
                CachedResponse cached = future.join();
                if (!cached.isExpired()) {
                    return (R) cached.data;
                } else {
                    responseCache.remove(cacheKey, future);
                }
            }

            CompletableFuture<CachedResponse> newFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        R result = objectMapper.readValue(response.body(), responseType);
                        return new CachedResponse(result, System.currentTimeMillis() + cacheTtlMillis);
                    } else {
                        throw new RuntimeException(new IOException("HTTP error " + response.statusCode() + ": " + response.body()));
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            responseCache.put(cacheKey, newFuture);
            return (R) newFuture.join().data;
        } else {
            // For non-GET requests or when cache is disabled, execute directly
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), responseType);
            } else {
                throw new IOException("HTTP error " + response.statusCode() + ": " + response.body());
            }
        }
    }

    public T get(ID id) throws IOException, InterruptedException {
        String endpoint = endpointConfig.findById().replace("{id}", id.toString());
        HttpRequest request = createRequestBuilder(endpoint)
                .GET()
                .build();
        T entity = sendRequest(request, entityType);
        relationshipResolver.resolve(entity, repositoryInformation);
        return entity;
    }

    public List<T> getAll() throws IOException, InterruptedException {
        HttpRequest request = createRequestBuilder(endpointConfig.findAll())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            List<T> entities = objectMapper.readValue(response.body(), objectMapper.getTypeFactory().constructCollectionType(List.class, entityType));
            entities.forEach(entity -> relationshipResolver.resolve(entity, repositoryInformation));
            return entities;
        } else {
            throw new IOException("HTTP error " + response.statusCode() + ": " + response.body());
        }
    }

    public T create(T entity) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(entity);
        HttpRequest request = createRequestBuilder(endpointConfig.create())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        
        if (cacheEnabled) {
            invalidateCacheForEntity(extractId(entity));
        }
        
        T newEntity = sendRequest(request, entityType);
        relationshipResolver.resolve(newEntity, repositoryInformation);
        return newEntity;
    }

    public T update(ID id, T entity) throws IOException, InterruptedException {
        String endpoint = endpointConfig.update().replace("{id}", id.toString());
        String json = objectMapper.writeValueAsString(entity);
        
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(json);
        HttpRequest request = switch (endpointConfig.updateMethod()) {
            case PUT -> createRequestBuilder(endpoint).PUT(bodyPublisher).build();
            case PATCH -> createRequestBuilder(endpoint).method("PATCH", bodyPublisher).build();
            default -> throw new UnsupportedOperationException("Unsupported HTTP method: " + endpointConfig.updateMethod());
        };
        
        // Invalidate relevant cache entries
        if (cacheEnabled) {
            invalidateCacheForEntity(id);
        }
        
        T updatedEntity = sendRequest(request, entityType);
        relationshipResolver.resolve(updatedEntity, repositoryInformation);
        return updatedEntity;
    }

    public void deleteInternal(ID id) throws IOException, InterruptedException {
        String endpoint = endpointConfig.delete().replace("{id}", id.toString());
        HttpRequest request = createRequestBuilder(endpoint)
                .DELETE()
                .build();
        
        // Invalidate relevant cache entries
        if (cacheEnabled) {
            invalidateCacheForEntity(id);
        }
        
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public EndpointConfig getEndpointConfig() {
        return endpointConfig;
    }

    private void invalidateCacheForEntity(ID id) {
        if (!cacheEnabled) return;
        // A simple approach is to clear the whole cache.
        // A more advanced implementation could selectively remove entries.
        responseCache.clear();
    }

    // Helper classes

    private record CachedResponse(Object data, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    // RepositoryAdapter interface implementation

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
        // Network repositories don't need creation - they exist on the remote server
        return TransactionResult.success(true);
    }

    @Override
    public DatabaseSession<ID, T, HttpClient> createSession() {
        return createSession(EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, HttpClient> createSession(EnumSet<SessionOption> options) {
        return new NetworkSession<>(this, options);
    }

    @Override
    public List<T> find(SelectQuery query) {
        try {
            if (query == null) {
                return getAll();
            }
            String queryString = buildQueryString(query);
            String endpoint = endpointConfig.findAll() + queryString;
            HttpRequest request = createRequestBuilder(endpoint)
                    .GET()
                    .build();

            @SuppressWarnings("unchecked")
            Class<List<T>> listType = (Class<List<T>>) (Class<?>) List.class;
            return sendRequest(request, listType);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to find entities", e);
        }
    }

    private static String buildQueryString(SelectQuery query) {
        StringBuilder sb = new StringBuilder();
        if (query.filters() != null && !query.filters().isEmpty()) {
            for (SelectOption filter : query.filters()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                } else {
                    sb.append('?');
                }
                sb.append(URLEncoder.encode(filter.option(), StandardCharsets.UTF_8));
                sb.append('=');
                sb.append(URLEncoder.encode(filter.value().toString(), StandardCharsets.UTF_8));
            }
        }

        if (query.sortOptions() != null && !query.sortOptions().isEmpty()) {
            for (SortOption sort : query.sortOptions()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                } else {
                    sb.append('?');
                }
                sb.append("sort=");
                sb.append(URLEncoder.encode(sort.field(), StandardCharsets.UTF_8));
                sb.append(',');
                sb.append(sort.order() == SortOrder.ASCENDING ? "asc" : "desc");
            }
        }

        if (query.limit() >= 0) {
            if (!sb.isEmpty()) {
                sb.append('&');
            } else {
                sb.append('?');
            }
            sb.append("limit=").append(query.limit());
        }

        return sb.toString();
    }

    @Override
    public List<T> find() {
        return find(null);
    }

    @Override
    public T findById(ID key) {
        try {
            return get(key);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to find entity by ID: " + key, e);
        }
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        if (keys.size() == 1) {
            return Collections.singletonMap(keys.iterator().next(), findById(keys.iterator().next()));
        }

        Map<ID, T> results = new HashMap<>();
        for (ID key : keys) {
            results.put(key, findById(key));
        }
        return results;
    }

    @Override
    public @NotNull CloseableIterator<T> findIterator(SelectQuery query) {
        Stream<T> stream = findStream(query);
        Iterator<T> it = stream.iterator();

        return new CloseableIterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public T next() {
                return it.next();
            }

            @Override
            public void close() {
                stream.close();
            }
        };
    }

    @Override
    public @NotNull Stream<T> findStream(SelectQuery query) {
        final int pageSize = query != null && query.limit() > 0
            ? query.limit()
            : DEFAULT_PAGE_SIZE;

        Iterator<T> iterator = new Iterator<>() {
            private int offset = 0;
            private Iterator<T> currentPage = Collections.emptyIterator();
            private boolean finished = false;

            private void loadNextPage() {
                if (finished) return;

                SelectQuery pageQuery = Query.select()
                    .where(query != null ? query.filters() : List.of())
                    .orderBy(query != null ? query.sortOptions() : List.of())
                    .limit(pageSize)
                    .build();

                List<T> page = find(pageQuery);

                if (page.isEmpty()) {
                    finished = true;
                    return;
                }

                offset += page.size();
                currentPage = page.iterator();
            }

            @Override
            public boolean hasNext() {
                while (!currentPage.hasNext() && !finished) {
                    loadNextPage();
                }
                return currentPage.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return currentPage.next();
            }
        };

        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
            false
        );
    }

    @Override
    public @Nullable T first(SelectQuery query) {
        List<T> results = find(query);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public TransactionResult<Boolean> insert(T value, TransactionContext<HttpClient> transactionContext) {
        try {
            create(value);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> value, TransactionContext<HttpClient> transactionContext) {
        try {
            for (T entity : value) {
                create(entity);
            }
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity, TransactionContext<HttpClient> transactionContext) {
        try {
            ID id = extractId(entity);
            update(id, entity);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity, TransactionContext<HttpClient> transactionContext) {
        try {
            ID id = extractId(entity);
            deleteInternal(id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T value) {
        return delete(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID entity, TransactionContext<HttpClient> transactionContext) {
        try {
            deleteInternal(entity);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        return deleteById(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> updateAll(
        @NotNull UpdateQuery query,
        TransactionContext<HttpClient> transactionContext) {

        try {
            List<T> targets = find(Query.select().where(query.filters()).build());
            if (targets.isEmpty()) {
                return TransactionResult.success(true);
            }

            for (T entity : targets) {
                // Apply field mutations
                for (var assignment : query.filters()) {
                    var field = repositoryInformation.getField(assignment.option());
                    if (field == null) continue;
                    field.setValue(entity, assignment.value());
                }

                ID id = extractId(entity);
                update(id, entity);
            }

            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        return updateAll(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> delete(
        DeleteQuery query,
        TransactionContext<HttpClient> tx) {

        try {
            SelectQuery select = Query.select().where(query.filters()).build();
            List<ID> ids = findIds(select);

            for (ID id : ids) {
                deleteInternal(id);
            }

            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        return delete(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        return insert(value, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity) {
        return updateAll(entity, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> query) {
        return insertAll(query, beginTransaction());
    }

    @Override
    public TransactionResult<Boolean> clear() {
        return TransactionResult.failure(new UnsupportedOperationException("Clear operation not supported for network repositories"));
    }

    @Override
    public TransactionResult<Boolean> createIndex(IndexOptions index) {
        // Network repositories don't support index creation from client side
        return TransactionResult.success(false);
    }

    @Override
    @NotNull
    public Class<T> getElementType() {
        return entityType;
    }

    // Helper methods

    public ID extractId(T entity) {
        try {
            var idField = repositoryInformation.getPrimaryKey();
            return idField.getValue(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract ID from entity", e);
        }
    }
}
