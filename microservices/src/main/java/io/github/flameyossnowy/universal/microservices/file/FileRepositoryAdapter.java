package io.github.flameyossnowy.universal.microservices.file;

import io.github.flameyossnowy.universal.api.CloseableIterator;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.ReadPolicy;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.annotations.FileRepository;
import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.GeneratedObjectFactories;
import io.github.flameyossnowy.universal.api.meta.GeneratedRelationshipLoaders;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.operation.Operation;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverBridge;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.microservices.file.executor.FileAggregationEngine;
import io.github.flameyossnowy.universal.microservices.file.executor.FileEntityStore;
import io.github.flameyossnowy.universal.microservices.file.executor.FileFilterEngine;
import io.github.flameyossnowy.universal.microservices.file.executor.FileIndexManager;
import io.github.flameyossnowy.universal.microservices.file.executor.FileMutationExecutor;
import io.github.flameyossnowy.universal.microservices.file.executor.FileQueryExecutor;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategies;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategy;
import io.github.flameyossnowy.universal.microservices.relationship.MicroserviceRelationshipHandler;
import io.github.flameyossnowy.universal.microservices.relationship.RelationshipResolver;
import io.github.flameyossnowy.uniform.json.JsonAdapter;
import io.github.flameyossnowy.uniform.json.JsonConfigBuilder;
import io.github.flameyossnowy.uniform.json.features.JsonReadFeature;
import io.github.flameyossnowy.uniform.json.features.JsonWriteFeature;
import io.github.flameyossnowy.uniform.json.resolvers.CoreTypeResolverRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * File-based {@link RepositoryAdapter} that persists entities as individual files.
 *
 * <ul>
 *   <li>{@link FileEntityStore}       – raw file I/O (read / write / delete)</li>
 *   <li>{@link FileFilterEngine}      – predicate / filter evaluation</li>
 *   <li>{@link FileQueryExecutor}     – find / count / stream queries</li>
 *   <li>{@link FileAggregationEngine} – aggregate &amp; window functions</li>
 *   <li>{@link FileIndexManager}      – secondary index lifecycle</li>
 *   <li>{@link FileMutationExecutor}  – insert / update / delete mutations</li>
 * </ul>
 *
 * @param <T>  entity type
 * @param <ID> primary-key type
 */
@SuppressWarnings("unused")
public class FileRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, FileContext> {

    private final Class<T> entityType;
    private final Class<ID> idType;
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final OperationExecutor<T, ID, FileContext> operationExecutor;
    private final OperationContext<T, ID, FileContext> operationContext;
    private final RelationshipHandler<T, ID> relationshipHandler;

    private final FileEntityStore<T, ID> entityStore;
    private final FileFilterEngine<T, ID> filterEngine;
    private final FileQueryExecutor<T, ID> queryExecutor;
    private final FileAggregationEngine<T, ID> aggregationEngine;
    private final FileIndexManager<T, ID> indexManager;
    private final FileMutationExecutor<T, ID> mutationExecutor;

    private final JsonAdapter objectMapper;

    FileRepositoryAdapter(
        @NotNull Class<T> entityType,
        @NotNull Class<ID> idType,
        @NotNull Path basePath,
        FileFormat format,
        boolean compressed,
        CompressionType compressionType,
        boolean sharding,
        int shardCount,
        IndexPathStrategy indexPathStrategy,
        boolean autoCreate,
        boolean parallelReads
    ) {
        this.entityType = entityType;
        this.idType     = idType;

        this.repositoryModel = GeneratedMetadata.getByEntityClass(entityType);
        if (repositoryModel == null) {
            throw new IllegalArgumentException(
                "Entity " + entityType.getName() + " must be annotated with @Repository");
        }

        this.resolverRegistry = new TypeResolverRegistry();
        for (Class<? extends TypeResolver<?>> resolverClass : repositoryModel.getRequiredResolvers()) {
            try {
                resolverRegistry.register(resolverClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate TypeResolver: " + resolverClass, e);
            }
        }

        JsonConfigBuilder jsonConfigBuilder = JsonAdapter.configBuilder()
            .addReadFeatures(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .addWriteFeatures(JsonWriteFeature.ESCAPE_UNICODE);

        TypeResolverBridge.registerAll(resolverRegistry, CoreTypeResolverRegistry.INSTANCE);
        JsonAdapter objectMapper = new JsonAdapter(jsonConfigBuilder
            .build());
        resolverRegistry.setJsonAdapterSupplier(() -> objectMapper);
        this.objectMapper = objectMapper;

        this.relationshipHandler = new MicroserviceRelationshipHandler<>(repositoryModel, idType, resolverRegistry);
        ObjectModel<T, ID> objectModel = GeneratedObjectFactories.getObjectModel(repositoryModel);
        RelationshipLoader<T, ID> relationshipLoader =
            GeneratedRelationshipLoaders.get(repositoryModel.tableName(), relationshipHandler, null, repositoryModel);
        RelationshipResolver<T, ID> relationshipResolver = new RelationshipResolver<>(relationshipHandler);

        this.operationExecutor = new FileOperationExecutor<>(this);
        this.operationContext  = new OperationContext<>(repositoryModel, resolverRegistry, operationExecutor);

        Path indexRoot = indexPathStrategy.resolveIndexRoot(basePath, entityType);

        this.entityStore = new FileEntityStore<>(
            entityType, repositoryModel, resolverRegistry, objectMapper,
            objectModel, relationshipLoader, relationshipResolver,
            basePath, format, compressed, compressionType, sharding, shardCount, parallelReads
        );

        this.filterEngine      = new FileFilterEngine<>(repositoryModel, objectMapper);
        this.queryExecutor     = new FileQueryExecutor<>(entityStore, filterEngine, repositoryModel);
        this.aggregationEngine = new FileAggregationEngine<>(repositoryModel, objectMapper, filterEngine, queryExecutor);
        this.indexManager      = new FileIndexManager<>(repositoryModel, objectMapper, indexRoot);
        this.mutationExecutor  = new FileMutationExecutor<>(repositoryModel, entityStore, filterEngine, indexManager);

        RepositoryRegistry.register(repositoryModel.tableName(), this);
        initDirectories(basePath, sharding, shardCount, autoCreate);
    }

    public static <T, ID> FileRepositoryBuilder<T, ID> builder(
        @NotNull Class<T> entityType,
        @NotNull Class<ID> idType
    ) {
        return new FileRepositoryBuilder<>(entityType, idType);
    }

    @ApiStatus.Obsolete
    public static <T, ID> FileRepositoryAdapter<T, ID> from(
        @NotNull Class<T> entityType,
        @NotNull Class<ID> idType
    ) {
        FileRepository annotation = entityType.getAnnotation(FileRepository.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                "Entity " + entityType.getName() + " must be annotated with @FileRepository");
        }

        return new FileRepositoryAdapter<>(
            entityType, idType,
            Paths.get(annotation.path()),
            annotation.format(),
            annotation.compressed(),
            annotation.compression(),
            annotation.sharding(),
            annotation.shardCount(),
            IndexPathStrategies.underBase(),
            true,
            false
        );
    }

    @Override @NotNull public Class<T>                            getEntityType()        { return entityType; }
    @Override @NotNull public Class<ID>                           getIdType()            { return idType; }
    @Override @NotNull public Class<T>                            getElementType()       { return entityType; }
    @Override @NotNull public RepositoryModel<T, ID>              getRepositoryModel()   { return repositoryModel; }
    @Override @NotNull public TypeResolverRegistry                getTypeResolverRegistry() { return resolverRegistry; }
    @Override @NotNull public OperationContext<T, ID, FileContext> getOperationContext()  { return operationContext; }
    @Override @NotNull public OperationExecutor<T, ID, FileContext> getOperationExecutor() { return operationExecutor; }
    @Override          public RelationshipHandler<T, ID>          getRelationshipHandler() { return relationshipHandler; }

    @Override @NotNull
    public TransactionContext<FileContext> beginTransaction() {
        return new FileTransactionContext();
    }

    @Override
    public DatabaseSession<ID, T, FileContext> createSession() {
        return createSession(EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, FileContext> createSession(EnumSet<SessionOption> options) {
        return new FileSession<>(this, options);
    }

    @Override @NotNull
    public <R> TransactionResult<R> execute(
        @NotNull Operation<T, ID, R, FileContext> operation,
        @NotNull TransactionContext<FileContext> transactionContext
    ) {
        return operation.executeWithTransaction(operationContext, transactionContext);
    }

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
        return TransactionResult.success(true);
    }

    @Override
    public List<T> find() {
        return find(ReadPolicy.NO_READ_POLICY);
    }

    @Override
    public List<T> find(ReadPolicy policy) {
        return find(null, policy);
    }

    @Override
    public List<T> find(SelectQuery query) {
        return find(query, ReadPolicy.NO_READ_POLICY);
    }

    @Override
    public List<T> find(SelectQuery query, ReadPolicy policy) {
        try {
            return query == null ? queryExecutor.findAll() : queryExecutor.find(query);
        } catch (IOException e) {
            throw new RuntimeException("Failed to find entities", e);
        }
    }

    @Override
    public @Nullable T findById(ID key) {
        try {
            return entityStore.read(key);
        } catch (IOException e) {
            throw new RuntimeException("Failed to find entity by ID: " + key, e);
        }
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        Map<ID, T> result = new HashMap<>(keys.size());
        for (ID id : keys) {
            try {
                T entity = entityStore.read(id);
                if (entity != null) result.put(id, entity);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read entity: " + id, e);
            }
        }
        return result;
    }

    @Override
    public @Nullable T first(SelectQuery query) {
        List<T> results = find(query);
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public @NotNull List<ID> findIds(SelectQuery query) {
        try {
            if (query == null)          return queryExecutor.findAllIds();
            if (query.limit() == 0)     return List.of();
            return queryExecutor.findIds(query);
        } catch (IOException e) {
            throw new RuntimeException("Failed to find IDs", e);
        }
    }

    @Override
    public long count(SelectQuery query, ReadPolicy policy) {
        try {
            return query == null ? queryExecutor.countAll() : queryExecutor.count(query);
        } catch (IOException e) {
            throw new RuntimeException("Failed to count entities", e);
        }
    }

    @Override
    public long count(ReadPolicy policy) {
        return count(null, policy);
    }

    @Override @NotNull
    public Stream<T> findStream(SelectQuery query) {
        return queryExecutor.stream(query);
    }

    @Override @NotNull
    public CloseableIterator<T> findIterator(SelectQuery query) {
        return queryExecutor.iterator(query);
    }

    // -------------------------------------------------------------------------
    // RepositoryAdapter – aggregation / window
    // -------------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> aggregate(@NotNull AggregationQuery query) {
        try {
            List<T> base = find(new SelectQuery(
                Collections.emptyList(), query.whereFilters(), Collections.emptyList(), -1, null));
            return aggregationEngine.aggregate(query, base);
        } catch (Exception e) {
            throw new RuntimeException("Failed to aggregate", e);
        }
    }

    @Override
    public <R> R aggregateScalar(@NotNull AggregationQuery query, @NotNull String fieldName, @NotNull Class<R> type) {
        List<Map<String, Object>> rows = aggregate(query);
        if (rows.isEmpty()) return null;
        return objectMapper.readValue(rows.getFirst(), type);
    }

    @Override
    public List<Map<String, Object>> window(@NotNull WindowQuery query) {
        List<T> base = find(new SelectQuery(
            Collections.emptyList(), query.whereFilters(), Collections.emptyList(), -1, null));

        if (!query.orderBy().isEmpty()) {
            queryExecutor.applySorting(base, query.orderBy());
        }

        return aggregationEngine.window(query, base);
    }

    @Override
    public <R> List<R> window(@NotNull WindowQuery query, @NotNull Class<R> resultType) {
        List<Map<String, Object>> rows = window(query);
        List<R> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(objectMapper.readValue(row, resultType));
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> executeAggregation(@NotNull Object rawQuery) {
        throw new UnsupportedOperationException("File adapter does not support raw aggregation execution");
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        return mutationExecutor.insert(value);
    }

    @Override
    public TransactionResult<Boolean> insert(T value, TransactionContext<FileContext> tx) {
        return mutationExecutor.insert(value);
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> entities) {
        return mutationExecutor.insertAll(entities);
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> entities, TransactionContext<FileContext> tx) {
        return mutationExecutor.insertAll(entities);
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity) {
        return mutationExecutor.updateEntity(entity);
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity, TransactionContext<FileContext> tx) {
        return mutationExecutor.updateEntity(entity);
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        return mutationExecutor.updateByQuery(query);
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<FileContext> tx) {
        return mutationExecutor.updateByQuery(query);
    }

    @Override
    public TransactionResult<Boolean> delete(T entity) {
        return mutationExecutor.deleteEntity(entity);
    }

    @Override
    public TransactionResult<Boolean> delete(T entity, TransactionContext<FileContext> tx) {
        return mutationExecutor.deleteEntity(entity);
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID id) {
        // Avoid reading entity when there are no indexes that require it
        if (indexManager.isEmpty()) {
            try {
                entityStore.delete(id);
                return TransactionResult.success(true);
            } catch (Exception e) {
                return TransactionResult.failure(e);
            }
        }
        T entity = findById(id);
        return mutationExecutor.deleteById(id, entity);
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID id, TransactionContext<FileContext> tx) {
        return deleteById(id);
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        return mutationExecutor.deleteByQuery(query);
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query, TransactionContext<FileContext> tx) {
        return mutationExecutor.deleteByQuery(query);
    }

    @Override
    public TransactionResult<Boolean> clear() {
        try {
            entityStore.clearCache();
            Path base = entityStore.basePath();

            if (entityStore.isSharding()) {
                for (int i = 0; i < entityStore.shardCount(); i++) {
                    Path shardPath = base.resolve(String.valueOf(i));
                    FileEntityStore.deleteDirectoryRecursively(shardPath);
                    Files.createDirectories(shardPath);
                }
            } else {
                FileEntityStore.deleteDirectoryRecursively(base);
                Files.createDirectories(base);
            }

            indexManager.clearAll();
            return TransactionResult.success(true);
        } catch (IOException e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> createIndex(@NotNull IndexOptions index) {
        try {
            List<T> allEntities = entityStore.readAll();
            return indexManager.createIndex(index, allEntities);
        } catch (IOException e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public void close() {
        entityStore.clearCache();
        RepositoryRegistry.unregister(repositoryModel.tableName());
    }

    // -------------------------------------------------------------------------
    // Package-visible accessors (used by FileOperationExecutor, FileSession, etc.)
    // -------------------------------------------------------------------------

    public FileEntityStore<T, ID> getEntityStore()         { return entityStore; }
    public FileFilterEngine<T, ID> getFilterEngine()       { return filterEngine; }
    public FileQueryExecutor<T, ID> getQueryExecutor()     { return queryExecutor; }
    public FileIndexManager<T, ID> getIndexManager()       { return indexManager; }
    public FileMutationExecutor<T, ID> getMutationExecutor() { return mutationExecutor; }

    /** Convenience extractor for callers within the package. */
    public ID extractId(T entity) {
        return repositoryModel.getPrimaryKeyValue(entity);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void initDirectories(
        Path basePath,
        boolean sharding,
        int shardCount,
        boolean autoCreate
    ) {
        try {
            if (Files.exists(basePath)) return;

            if (!autoCreate) {
                throw new IllegalStateException(
                    "Base path does not exist and autoCreate is disabled: " + basePath);
            }

            Files.createDirectories(basePath);
            if (sharding) {
                for (int i = 0; i < shardCount; i++) {
                    Files.createDirectories(basePath.resolve(String.valueOf(i)));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory: " + basePath, e);
        }
    }
}