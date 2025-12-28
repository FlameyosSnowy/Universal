package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.*;
import io.github.flameyossnowy.universal.api.annotations.Index;
import io.github.flameyossnowy.universal.api.cache.*;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.handler.DefaultExceptionHandler;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.reflect.*;

import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.SimpleTransactionContext;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.query.SQLQueryValidator;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.LongFunction;

@SuppressWarnings("unchecked")
public class AbstractRelationalRepositoryAdapter<T, ID> implements RelationalRepositoryAdapter<T, ID> {
    protected final SQLConnectionProvider dataSource;
    protected final ExceptionHandler<T, ID, Connection> exceptionHandler;
    protected final Class<T> repository;
    protected final Class<ID> idClass;
    protected final DefaultResultCache<String, T, ID> cache;
    protected final SessionCache<ID, T> globalCache;
    protected final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;
    protected final RepositoryInformation repositoryInformation;
    protected RelationalObjectFactory<T, ID> objectFactory;
    protected final QueryParseEngine engine;
    protected final TypeResolverRegistry resolverRegistry;

    private final AuditLogger<T> auditLogger;
    private final EntityLifecycleListener<T> entityLifecycleListener;

    // Advanced caching features
    protected final SecondLevelCache<ID, T> l2Cache;
    protected final ReadThroughCache<ID, T> readThroughCache;
    protected final PrefetchingCache<ID, T> prefetchingCache;

    protected long openedSessions = 1;
    
    // Operation-based API support
    protected final OperationContext<Connection> operationContext;
    protected final OperationExecutor<Connection> operationExecutor;
    protected final QueryValidator queryValidator;

    protected AbstractRelationalRepositoryAdapter(
            SQLConnectionProvider dataSource,
            DefaultResultCache<String, T, ID> cache,
            @NotNull Class<T> repository,
            Class<ID> idClass,
            QueryParseEngine.SQLType type,
            SessionCache<ID, T> globalCache,
            LongFunction<SessionCache<ID, T>> sessionCacheSupplier,
            CacheWarmer<T, ID> cacheWarmer) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        this.idClass = idClass;
        this.dataSource = dataSource;
        this.cache = cache;
        this.repository = repository;
        this.globalCache = globalCache;
        Logging.info("Initializing repository: " + repository.getSimpleName());

        this.repositoryInformation = RepositoryMetadata.getMetadata(repository);
        if (repositoryInformation == null)
            throw new IllegalArgumentException("Could not find repository information for class: " + repository.getSimpleName());
        RepositoryRegistry.register(this.repositoryInformation.getRepositoryName(), this);
        Logging.deepInfo("Repository information: " + repositoryInformation);

        this.resolverRegistry = new TypeResolverRegistry();
        this.objectFactory = new ObjectFactory<>(repositoryInformation, dataSource, this, resolverRegistry);

        ExceptionHandler<T, ID, Connection> exceptionHandler = (ExceptionHandler<T, ID, Connection>) repositoryInformation.getExceptionHandler();
        this.exceptionHandler = exceptionHandler == null ? new DefaultExceptionHandler<>() : exceptionHandler;

        Logging.info("Creating QueryParseEngine for query generation for table " + repositoryInformation.getRepositoryName() + " with type: " + type.name() + '.');
        this.engine = new QueryParseEngine(type, repositoryInformation, resolverRegistry);
        Logging.info("Successfully created QueryParseEngine for table: " + repositoryInformation.getRepositoryName());

        this.entityLifecycleListener = (EntityLifecycleListener<T>) repositoryInformation.getEntityLifecycleListener();
        this.auditLogger = (AuditLogger<T>) repositoryInformation.getAuditLogger();
        
        // Initialize advanced caches
        this.l2Cache = new SecondLevelCache<>(10000, 300000, io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED);
        this.readThroughCache = new ReadThroughCache<>(10000, io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED, this::loadFromDatabase);
        this.prefetchingCache = new PrefetchingCache<>(3, 10);
        
        Logging.info("Advanced caching enabled: L2 Cache, Read-Through Cache, Prefetching Cache");

        // Initialize operation-based API support
        this.operationExecutor = new SQLOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(
                repositoryInformation,
                resolverRegistry,
                operationExecutor
        );
        this.queryValidator = new SQLQueryValidator(repositoryInformation, type.getDialect());

        if (cacheWarmer != null) {
            cacheWarmer.warmCache(this);
        }
    }

    protected void setObjectFactory(RelationalObjectFactory<T, ID> objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public List<T> find(SelectQuery q) {
        String query = engine.parseSelect(q, false);
        return executeQueryWithParams(query, q == null ? List.of() : q.filters());
    }

    private @NotNull List<ID> extractIds(ResultSet rs) throws SQLException {
        FieldData<?> primaryKey = validatePrimaryKey();

        String idColumn = primaryKey.name();

        TypeResolver<ID> resolver = resolverRegistry.resolve(idClass);

        List<ID> list = new ArrayList<>(rs.getMetaData().getColumnCount());

        SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry);
        while (rs.next()) {
            ID id = resolver.resolve(result, idColumn);
            list.add(id);
        }

        return list;
    }

    private @NotNull List<T> search(String query, boolean first, @NotNull List<SelectOption> filters) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, query);
            this.addFilterToPreparedStatement(filters, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return first ? fetchFirst(query, resultSet) : fetchAll(query, resultSet);
            }
        }
    }

    private @NotNull List<T> fetchAll(String query, ResultSet resultSet) throws Exception {
        // Set fetch size with reasonable default if not configured
        int fetchSize = repositoryInformation.getFetchPageSize();
        if (fetchSize <= 0) fetchSize = 100; // Reasonable default
        resultSet.setFetchSize(fetchSize);
        
        return insertToCache(query, repositoryInformation.hasRelationships()
                ? mapResultsWithRelationships(query, resultSet, new ArrayList<>(fetchSize))
                : mapResults(query, resultSet, new ArrayList<>(fetchSize)));
    }

    private @NotNull List<T> fetchFirst(String query, @NotNull ResultSet resultSet) throws Exception {
        if (!resultSet.next()) return insertToCache(query, List.of());
        return insertToCache(query, List.of(repositoryInformation.hasRelationships() ? this.objectFactory.createWithRelationships(resultSet) : this.objectFactory.create(resultSet)));
    }

    @Override
    public List<T> find() {
        return executeQuery(engine.parseSelect(null, false));
    }

    @Override
    public T findById(ID key) {
        FieldData<?> primaryKey = validatePrimaryKey();

        // Check L2 cache first
        T cached = l2Cache.get(key);
        if (cached != null) {
            Logging.deepInfo("L2 cache hit for ID: " + key);
            return cached;
        }
        
        // Load from database
        T entity = first(Query.select().where(primaryKey.name(), key).build());
        
        // Store in L2 cache
        if (entity != null) {
            l2Cache.put(key, entity);
        }
        
        return entity;
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        FieldData<?> primaryKey = validatePrimaryKey();

        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        if (keys.size() == 1) {
            ID next = keys.iterator().next();
            return Collections.singletonMap(next, findById(next));
        }

        SelectQuery query = Query.select().where(primaryKey.name(), keys).build();
        String s = engine.parseSelect(query, false);

        List<T> ts = executeQueryWithParams(s, false, query.filters());
        Map<ID, T> result = new HashMap<>(ts.size());
        for (T t : ts) {
            ID id = primaryKey.getValue(t);
            result.put(id, t);
            l2Cache.put(id, t);
            readThroughCache.put(id, t);
        }
        return result;
    }

    /**
     * Helper method to load entity from database (used by ReadThroughCache).
     */
    private T loadFromDatabase(ID key) {
        FieldData<?> primaryKey = validatePrimaryKey();
        return first(Query.select().where(primaryKey.name(), key).build());
    }

    @Override
    public @Nullable T first(final SelectQuery q) {
        String query = engine.parseSelect(q, true);
        List<T> results = executeQueryWithParams(query, true, q.filters());
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public TransactionResult<Boolean> insert(@NotNull T value, TransactionContext<Connection> transactionContext) {
        return executeInsertAndSetId(transactionContext, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> value, TransactionContext<Connection> transactionContext) {
        if (value.isEmpty()) return TransactionResult.success(false);
        return executeBatch(transactionContext, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> insertAll(@NotNull Collection<T> collection) {
        if (collection.isEmpty()) return TransactionResult.success(false);
        return executeBatch(null, engine.parseInsert(), collection);
    }

    private static final int BATCH_SIZE = 1000; // Prevent OOM on large batches
    
    private TransactionResult<Boolean> executeBatch(TransactionContext<Connection> transactionContext, String sql, Collection<T> collection) {
        try (Connection connection = transactionContext == null ? dataSource.getConnection() : transactionContext.connection();
             PreparedStatement statement = dataSource.prepareStatement(sql, connection)) {

            connection.setAutoCommit(false);
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            try {
                // Process in chunks to prevent OOM and timeout issues
                int i = 0;
                for (T entity : collection) {
                    objectFactory.insertEntity(parameters, entity);
                    statement.addBatch();
                    
                    // Execute batch every BATCH_SIZE items
                    if ((i + 1) % BATCH_SIZE == 0) {
                        statement.executeBatch();
                        statement.clearBatch();
                    }

                    i++;
                }
                
                // Execute remaining items
                if (collection.size() % BATCH_SIZE != 0) {
                    statement.executeBatch();
                }
                
                connection.commit();

                FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
                if (primaryKey != null) {
                    // Insert collection entities after main batch
                    for (T entity : collection) {
                        objectFactory.insertCollectionEntities(entity, primaryKey.getValue(entity), parameters);
                    }
                }

                if (cache != null) cache.clear();

                return TransactionResult.success(true);
            } catch (Exception e) {
                connection.rollback();
                return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
            }
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }


    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity, TransactionContext<Connection> transactionContext) {
        FieldData<?> primaryKey = validatePrimaryKey();

        ID id = primaryKey.getValue(entity);
        String sql = engine.parseUpdateFromEntity();
        TransactionResult<Boolean> result = executeUpdate(transactionContext, sql, statement -> {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            this.setUpdateParameters(parameters, entity);
        }, entity, id);
        
        if (result.isSuccess()) {
            // Invalidate L2 cache for this entity
            try {
                l2Cache.invalidate(id);
                readThroughCache.invalidate(id);
                Logging.deepInfo("Invalidated caches for entity ID: " + id);
            } catch (Exception e) {
                Logging.error("Failed to invalidate cache: " + e.getMessage());
            }
        }
        
        return result;
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull T entity, TransactionContext<Connection> transactionContext) {
        return executeDelete(transactionContext, engine.parseDelete(entity), entity);
    }

    @Override
    public TransactionResult<Boolean> delete(T value) {
        return executeDelete(null, engine.parseDelete(value), value);
    }

    @Override
    public TransactionResult<Boolean> deleteById(@NotNull ID entity, TransactionContext<Connection> transactionContext) {
        validatePrimaryKey();
        return executeDeleteWithId(transactionContext, engine.parseDelete(entity), entity);
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        validatePrimaryKey();
        return executeDeleteWithId(null, engine.parseDelete(value), value);
    }

    @Override
    public TransactionResult<Boolean> createIndex(IndexOptions index) {
        return executeRawQuery(engine.parseIndex(index));
    }

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
       return executeRawQuery(engine.parseRepository(ifNotExists))
                .flatMap((result) -> {
                    for (Index index : repositoryInformation.getIndexes()) {
                        TransactionResult<Boolean> indexResult = executeRawQuery(engine.parseIndex(IndexOptions.builder(repository)
                                .indexName(index.name())
                                .fields(index.fields())
                                .type(index.type())
                                .build()));
                        if (indexResult.isError()) {
                            return indexResult;
                        }
                    }
                    return TransactionResult.success(true);
                });
    }

    private @NotNull List<T> mapResults(String query, @NotNull ResultSet resultSet, List<T> results) throws Exception {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();

        boolean existingGlobalCache = globalCache != null;
        if (existingGlobalCache && primaryKey == null) {
            throw new IllegalArgumentException("Cannot extract primary key from " + repositoryInformation.getRepositoryName() + " because there's no id.");
        }

        while (resultSet.next()) {
            T entity = this.objectFactory.create(resultSet);
            if (existingGlobalCache)
                globalCache.put(primaryKey.getValue(entity), entity);
            results.add(entity);
        }
        return insertToCache(query, results);
    }

    private List<T> insertToCache(String query, List<T> result) {
        if (cache != null) cache.insert(query, result, (entity) -> {
            FieldData<?> primaryKey = validatePrimaryKey();
            return primaryKey.getValue(entity);
        });
        return result;
    }

    private @NotNull List<T> mapResultsWithRelationships(String query, @NotNull ResultSet resultSet, List<T> fetchedData) throws Exception {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();

        boolean existingGlobalCache = globalCache != null;
        if (existingGlobalCache) {
            if (primaryKey == null) {
                throw new IllegalArgumentException("Cannot extract primary key from " + repositoryInformation.getRepositoryName() + " because there's no id.");
            }
        }

        while (resultSet.next()) {
            T entity = this.objectFactory.createWithRelationships(resultSet);
            if (globalCache != null) globalCache.put(primaryKey.getValue(entity), entity);
            fetchedData.add(entity);
        }
        return insertToCache(query, fetchedData);
    }

    @Override
    public @NotNull TransactionContext<Connection> beginTransaction() {
        try {
            return new SimpleTransactionContext(dataSource.getConnection());
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage());
        }
    }

    @Override
    public @NotNull List<ID> findIds(@NotNull SelectQuery query) {
        String sql = engine.parseQueryIds(query, query.limit() == 1);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(sql, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            this.addFilterToPreparedStatement(query.filters(), parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return extractIds(resultSet);
            }
        } catch (Exception e) {
            return this.exceptionHandler.handleReadIds(e, repositoryInformation, query, this);
        }
    }

    private void addFilterToPreparedStatement(List<SelectOption> filters, SQLDatabaseParameters parameters) {
        for (SelectOption value : filters) {
            if (value == null) continue;

            // Handle IN clause with list of values
            if ("IN".equalsIgnoreCase(value.operator()) && value.value() instanceof Collection<?> list) {
                for (Object item : list) {
                    TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(item.getClass());
                    resolver.insert(parameters, value.option(), item);
                }
            } else {
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.value().getClass());
                resolver.insert(parameters, value.option(), value.value());
            }
        }
    }


    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        String sql = engine.parseUpdate(query);
        return executeUpdate(transactionContext, sql, statement -> {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            setUpdateParameters(query, parameters);
        });
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query, TransactionContext<Connection> transactionContext) {
        return executeDelete(transactionContext, engine.parseDelete(query), query);
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        String sql = engine.parseUpdate(query);
        return executeUpdate(null, sql, statement -> {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            setUpdateParameters(query, parameters);
        });
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        return executeDelete(null, engine.parseDelete(query), query);
    }

    @Override
    public DatabaseSession<ID, T, Connection> createSession() {
        openedSessions++;
        return new DefaultSession<>(this, sessionCacheSupplier.apply(openedSessions), openedSessions, EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, Connection> createSession(EnumSet<SessionOption> options) {
        openedSessions++;
        return new DefaultSession<>(this, sessionCacheSupplier.apply(openedSessions), openedSessions, options);
    }

    @Override
    public TransactionResult<Boolean> clear() {
        return executeRawQuery("DELETE FROM " + repositoryInformation.getRepositoryName());
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        return executeInsertAndSetId(null, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreUpdate(entity);
        T oldEntity = null;

        FieldData<?> primaryKey = validatePrimaryKey();
        if (auditLogger != null) oldEntity = findById(primaryKey.getValue(entity));

        String sql = engine.parseUpdateFromEntity();
        TransactionResult<Boolean> result = executeUpdate(null, sql, statement -> {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            this.setUpdateParameters(parameters, entity);
        });
        if (result.isSuccess()) {
            if (entityLifecycleListener != null)entityLifecycleListener.onPostUpdate(entity);
            if (auditLogger != null) auditLogger.onUpdate(oldEntity, entity);
        }
        return result;
    }

    private @NotNull FieldData<?> validatePrimaryKey() {
        FieldData<?> primaryKey = repositoryInformation.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Cannot extract primary key from " + repositoryInformation.getRepositoryName() + " because there's no id.");
        }
        return primaryKey;
    }

    @Override
    public @NotNull Class<ID> getIdType() {
        return idClass;
    }

    @Override
    public @NotNull RepositoryInformation getRepositoryInformation() {
        return repositoryInformation;
    }

    @Override
    public Class<T> getElementType() {
        return repository;
    }

    @Override
    @NotNull
    public OperationContext<Connection> getOperationContext() {
        return operationContext;
    }

    @Override
    @NotNull
    public OperationExecutor<Connection> getOperationExecutor() {
        return operationExecutor;
    }

    @Override
    @NotNull
    public TypeResolverRegistry getTypeResolverRegistry() {
        return resolverRegistry;
    }

    @Override
    public TransactionResult<Boolean> executeRawQuery(final String query) {
        Logging.info("Parsed query: " + query);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(query, connection)) {
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryInformation, this);
        }
    }

    @Override
    public List<T> executeQuery(String query, Object... params) {
        List<T> result;
        try {
            return cache != null && (result = cache.fetch(query)) != null ? result : search(query, false, List.of());
        } catch (Exception e) {
            return this.exceptionHandler.handleRead(e, repositoryInformation, null, this);
        }
    }

    @Override
    public List<T> executeQueryWithParams(String query, List<SelectOption> params) {
        return executeQueryWithParams(query, false, params);
    }

    @Override
    public List<T> executeQueryWithParams(String query, boolean first, List<SelectOption> params) {
        List<T> result = cache == null ? null : cache.fetch(query);
        try {
            return result != null ? result : search(query, first, params);
        } catch (Exception e) {
            return this.exceptionHandler.handleRead(e, repositoryInformation, null, this);
        }
    }

    private TransactionResult<Boolean> executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter, T entity, ID id) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreUpdate(entity);
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            if (globalCache != null) globalCache.put(id, entity);

            T oldEntity = null;
            if (auditLogger != null) oldEntity = findById(id);

            TransactionResult<Boolean> success = TransactionResult.success(statement.execute());
            if (auditLogger != null) auditLogger.onUpdate(oldEntity, entity);
            if (entityLifecycleListener != null) entityLifecycleListener.onPostUpdate(entity);
            return success;
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryInformation, this);
        }
    }


    private TransactionResult<Boolean> executeDelete(TransactionContext<Connection> transactionContext, String sql, T entity) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreDelete(entity);
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);

            FieldData<?> primaryKey = validatePrimaryKey();

            return processDelete(primaryKey.getValue(entity), parameters, statement, entity);
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeDelete(TransactionContext<Connection> transactionContext, String sql, DeleteQuery query) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            setUpdateParameters(query, parameters);
            if (cache != null) cache.clear();
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeDeleteWithId(TransactionContext<Connection> transactionContext, String sql, ID id) {
        T byId = null;
        if (auditLogger != null || entityLifecycleListener != null) byId = findById(id);
        if (entityLifecycleListener != null) entityLifecycleListener.onPreDelete(byId);
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);
            return processDelete(id, parameters, statement, byId);
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    private @NotNull TransactionResult<Boolean> processDelete(ID id, SQLDatabaseParameters parameters, PreparedStatement statement, T entity) throws SQLException {
        TypeResolver<ID> resolver = resolverRegistry.resolve(idClass);

        // Primary key is always not null
        //noinspection DataFlowIssue
        resolver.insert(parameters, repositoryInformation.getPrimaryKey().name(), id);

        if (cache != null) cache.clear();
        if (globalCache != null) globalCache.remove(id);

        TransactionResult<Boolean> success = TransactionResult.success(statement.execute());
        if (auditLogger != null) auditLogger.onDelete(entity);
        if (entityLifecycleListener != null) entityLifecycleListener.onPostDelete(entity);
        return success;
    }

    private TransactionResult<Boolean> executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeInsertAndSetId(TransactionContext<Connection> transactionContext, String sql, T value) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreInsert(value);
        try (Connection connection = transactionContext == null ? dataSource.getConnection() : transactionContext.connection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql);

            this.objectFactory.insertEntity(parameters, value);

            if (statement.executeUpdate() > 0) {
                if (repositoryInformation.getPrimaryKey() == null) {
                    return TransactionResult.success(true);
                }
                if (!repositoryInformation.getPrimaryKey().autoIncrement()) {
                    this.objectFactory.insertCollectionEntities(value, repositoryInformation.getPrimaryKey().getValue(value), parameters);
                    if (globalCache != null) globalCache.put(repositoryInformation.getPrimaryKey().getValue(value), value);
                    return TransactionResult.success(true);
                }
                ResultSet generatedKeys = statement.getGeneratedKeys();
                SQLDatabaseResult result = new SQLDatabaseResult(generatedKeys, resolverRegistry);
                if (generatedKeys.next()) {
                    TypeResolver<ID> resolver = resolverRegistry.resolve(idClass);
                    ID generatedId = resolver.resolve(result, repositoryInformation.getPrimaryKey().name());

                    repositoryInformation.getPrimaryKey().setValue(value, generatedId);

                    if (globalCache != null) globalCache.put(generatedId, value);

                    this.objectFactory.insertCollectionEntities(value, generatedId, parameters);
                }
                if (cache != null) cache.clear();
                if (auditLogger != null) auditLogger.onInsert(value);
                if (entityLifecycleListener != null) entityLifecycleListener.onPostInsert(value);

                return TransactionResult.success(true);
            }
            return TransactionResult.success(false);
        } catch (Exception exception) {
            return this.exceptionHandler.handleInsert(exception, repositoryInformation, this);
        }
    }

    private void setUpdateParameters(SQLDatabaseParameters statement, @NotNull T entity) {
        for (FieldData<?> fieldData : repositoryInformation.getFields()) {
            if (fieldData.autoIncrement() || fieldData.oneToMany() != null) {
                continue; // Skip auto-incrementing fields and collection-based relationships
            }

            Object value = fieldData.getValue(entity);
            Logging.deepInfo("Processing field for update: " + fieldData.name() + " with value: " + value);

            // Handle relationship fields
            if ((fieldData.oneToOne() != null || fieldData.manyToOne() != null)) {
                if (value == null) {
                    // If the relationship is null, we still need to set the foreign key to null
                    Logging.deepInfo("  -> Relationship field is null, setting to NULL");
                } else {
                    // For non-null relationships, get the ID from the related entity
                    RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(fieldData.type());
                    if (relatedInfo != null) {
                        FieldData<?> pkField = relatedInfo.getPrimaryKey();
                        if (pkField == null) {
                            throw new IllegalArgumentException("Cannot extract primary key from " + repositoryInformation.getRepositoryName() + " because there's no id.");
                        }

                        value = pkField.getValue(value);
                        Logging.deepInfo("  -> Relationship field, using ID for update: " + value);
                    }
                }
            }

            // Get the appropriate type resolver
            TypeResolver<Object> resolver;
            if ((fieldData.oneToOne() != null || fieldData.manyToOne() != null) && value != null) {
                // For relationship fields, use the type of the ID field
                RepositoryInformation relatedInfo = RepositoryMetadata.getMetadata(fieldData.type());
                if (relatedInfo != null) {
                    FieldData<?> pkField = relatedInfo.getPrimaryKey();
                    if (pkField == null) {
                        throw new IllegalArgumentException("Cannot extract primary key from " + repositoryInformation.getRepositoryName() + " because there's no id.");
                    }

                    resolver = (TypeResolver<Object>) resolverRegistry.resolve(pkField.type());
                } else {
                    resolver = (TypeResolver<Object>) resolverRegistry.resolve(fieldData.type());
                }
            } else {
                // For regular fields, use the field's type
                resolver = (TypeResolver<Object>) resolverRegistry.resolve(fieldData.type());
            }

            if (resolver == null) {
                Logging.deepInfo("No resolver for " + fieldData.type() + ", assuming it's a relationship handled elsewhere.");
                continue;
            }

            Logging.deepInfo("Binding parameter " + fieldData.name() + ": " + value + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
            resolver.insert(statement, fieldData.name(), value);
        }
    }

    private void setUpdateParameters(@NotNull UpdateQuery query, SQLDatabaseParameters parameters) {
        for (var value : query.updates().entrySet()) {
            TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.getValue().getClass());
            resolver.insert(parameters, value.getKey(), value.getValue());
        }

        List<SelectOption> conditions = query.filters();
        if (conditions.isEmpty()) return;
        for (SelectOption value : conditions) {
            TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.value().getClass());
            resolver.insert(parameters, value.option(), value.value());
        }
    }

    private void setUpdateParameters(@NotNull DeleteQuery query, SQLDatabaseParameters parameters) {
        for (SelectOption value : query.filters()) {
            TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.resolve(value.value().getClass());
            resolver.insert(parameters, value.option(), value.value());
        }
    }

    private interface StatementSetter {
        void set(PreparedStatement statement) throws Exception;
    }
}