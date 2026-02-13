package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.*;
import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.cache.*;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.handler.DefaultExceptionHandler;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.GeneratedObjectFactories;
import io.github.flameyossnowy.universal.api.meta.GeneratedRelationshipLoaders;
import io.github.flameyossnowy.universal.api.meta.IndexModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.operation.OperationContext;
import io.github.flameyossnowy.universal.api.operation.OperationExecutor;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;

import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.SimpleTransactionContext;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlCacheManager;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlIteratorBuilder;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlParameterBinder;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlQueryExecutor;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlReadExecutor;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlResultMapper;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlWriteExecutor;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.query.SQLQueryValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class AbstractRelationalRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, Connection> {
    protected final SQLConnectionProvider dataSource;
    protected final ExceptionHandler<T, ID, Connection> exceptionHandler;
    protected final Class<T> repository;
    protected final Class<ID> idClass;
    protected final DefaultResultCache<String, T, ID> cache;
    protected final SessionCache<ID, T> globalCache;
    protected final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;
    protected final QueryParseEngine<T, ID> engine;
    protected final TypeResolverRegistry resolverRegistry;

    private final AuditLogger<T> auditLogger;
    private final EntityLifecycleListener<T> entityLifecycleListener;

    @Nullable
    protected final SecondLevelCache<ID, T> l2Cache;

    @Nullable
    protected final ReadThroughCache<ID, T> readThroughCache;

    private final boolean supportsArrays;
    private final QueryParseEngine.SQLType sqlType;

    protected long openedSessions = 1;

    protected final OperationContext<T, ID, Connection> operationContext;
    protected final OperationExecutor<T, ID, Connection> operationExecutor;
    protected final QueryValidator queryValidator;

    private final ObjectModel<T, ID> objectModel;
    private final RepositoryModel<T, ID> repositoryModel;
    private final CollectionHandler collectionHandler;

    private final SqlParameterBinder<T, ID> parameterBinder;
    private final SqlResultMapper<T, ID> resultMapper;
    private final SqlWriteExecutor<T, ID> writeExecutor;
    private final SqlCacheManager<T, ID> cacheManager;
    private final SqlQueryExecutor<T, ID> queryExecutor;
    private final SqlIteratorBuilder<T, ID> iteratorBuilder;

    protected AbstractRelationalRepositoryAdapter(
            SQLConnectionProvider dataSource,
            DefaultResultCache<String, T, ID> cache,
            @NotNull Class<T> repository,
            Class<ID> idClass,
            QueryParseEngine.SQLType sqlType,
            SessionCache<ID, T> globalCache,
            LongFunction<SessionCache<ID, T>> sessionCacheSupplier,
            CacheWarmer<T, ID> cacheWarmer,
            boolean cacheEnabled,
            int maxSize) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        this.idClass = idClass;
        this.dataSource = dataSource;
        this.cache = cache;
        this.repository = repository;
        this.globalCache = globalCache;
        this.supportsArrays = sqlType.supportsArrays();
        this.sqlType = sqlType;
        this.repositoryModel = GeneratedMetadata.getByEntityClass(repository);

        Objects.requireNonNull(repositoryModel);

        ExceptionHandler<T, ID, Connection> exceptionHandler = (ExceptionHandler<T, ID, Connection>) repositoryModel.getExceptionHandler();
        this.exceptionHandler = exceptionHandler == null ? new DefaultExceptionHandler<>() : exceptionHandler;

        Logging.info(() -> "Initializing repository: " + repository.getSimpleName());

        RepositoryRegistry.register(this.repositoryModel.tableName(), this);
        Logging.deepInfo(() -> "Repository information: " + repositoryModel);

        this.resolverRegistry = new TypeResolverRegistry();
        for (Class<? extends TypeResolver<?>> resolverClass : repositoryModel.getRequiredResolvers()) {
            try {
                this.resolverRegistry.register(resolverClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate TypeResolver: " + resolverClass, e);
            }
        }

        Logging.info(() -> "Creating QueryParseEngine for query generation for table " + repositoryModel.tableName() + " with sqlType: " + sqlType.name() + '.');
        this.engine = new QueryParseEngine<>(sqlType, repositoryModel, resolverRegistry, dataSource);
        Logging.info(() -> "Successfully created QueryParseEngine for table: " + repositoryModel.tableName());

        this.entityLifecycleListener = repositoryModel.getEntityLifecycleListener();
        this.auditLogger = repositoryModel.getAuditLogger();
        
        if (cacheEnabled) {
            this.l2Cache = new SecondLevelCache<>(maxSize, 300000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED);
            this.readThroughCache = new ReadThroughCache<>(maxSize, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED, this::loadFromDatabase);
        } else {
            this.l2Cache = null;
            this.readThroughCache = null;
        }

        this.operationExecutor = new SQLOperationExecutor<>(this);
        this.operationContext = new OperationContext<>(
                repositoryModel,
                resolverRegistry,
                operationExecutor
        );
        this.queryValidator = new SQLQueryValidator<>(repositoryModel, sqlType.getDialect());
        if (cacheWarmer != null) cacheWarmer.warmCache(this);

        RelationshipHandler<T, ID> relationshipHandler = new SQLRelationshipHandler<>(repositoryModel, idClass, resolverRegistry);
        this.collectionHandler = new SQLCollectionHandler(dataSource, this.resolverRegistry, supportsArrays);
        RelationshipLoader<T, ID> relationshipLoader = GeneratedRelationshipLoaders.get(
            repositoryModel.tableName(),
            relationshipHandler,
            collectionHandler,
            repositoryModel
        );

        this.objectModel = GeneratedObjectFactories.getObjectModel(repositoryModel);
        this.parameterBinder = new SqlParameterBinder<>();
        this.resultMapper = new SqlResultMapper<>(
            repositoryModel, idClass, resolverRegistry, objectModel,
            relationshipLoader, globalCache, cache
        );
        this.cacheManager = new SqlCacheManager<>(cache, objectModel, cacheEnabled, l2Cache, readThroughCache);
        SqlReadExecutor<T, ID> readExecutor = new SqlReadExecutor<>(
            dataSource, collectionHandler, supportsArrays, sqlType, parameterBinder,
            resultMapper, cacheManager
        );

        this.queryExecutor = new SqlQueryExecutor<>(dataSource, this.exceptionHandler, cache, repositoryModel, readExecutor, this);

        String[] primaryKeyColumnNames = new String[] { repositoryModel.getPrimaryKey().name() };
        boolean isAutoIncrement = repositoryModel.getPrimaryKey() != null && repositoryModel.getPrimaryKey().autoIncrement();
        this.writeExecutor = new SqlWriteExecutor<>(
            dataSource, repositoryModel, resolverRegistry, collectionHandler,
            supportsArrays, cache, globalCache, relationshipHandler, this.exceptionHandler,
            this, objectModel, idClass, auditLogger, entityLifecycleListener, parameterBinder,
            sqlType, isAutoIncrement, primaryKeyColumnNames
        );

        this.iteratorBuilder = new SqlIteratorBuilder<>(
            dataSource, repositoryModel, resolverRegistry, collectionHandler, supportsArrays,
            objectModel, idClass, parameterBinder, sqlType, resultMapper, engine, relationshipLoader
        );

        engine.parseRepository(true);
        for (IndexModel index : repositoryModel.indexes()) {
            TransactionResult<Boolean> indexResult = queryExecutor.executeRawQuery(engine.parseIndex(IndexOptions.builder(repository)
                .indexName(index.name())
                .rawFields(index.fields())
                .type(index.type())
                .build()));
            if (indexResult.isError()) {
                Logging.error("Failed to create index: " + index.name() + " for repository: " + repositoryModel.tableName());
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
        RepositoryRegistry.unregister(repositoryModel.tableName());
    }

    @Override
    public List<T> find(SelectQuery q) {
        String query = engine.parseSelect(q, false);
        return queryExecutor.executeQueryWithParams(query, q, q == null ? List.of() : q.filters());
    }

    @Override
    public List<T> find() {
        return queryExecutor.executeQuery(engine.parseSelect(null, false));
    }

    @Override
    public T findById(ID key) {
        FieldModel<T> primaryKey = this.repositoryModel.getPrimaryKey();
        if (l2Cache == null) return first(Query.select().where(primaryKey.name()).eq(key).build());

        T cached = l2Cache.get(key);
        if (cached != null) {
            Logging.deepInfo(() -> "L2 cache hit for ID: " + key);
            return cached;
        }
        
        T entity = first(Query.select().where(primaryKey.name()).eq(key).build());
        
        if (entity != null) {
            l2Cache.put(key, entity);
        }
        
        return entity;
    }

    @Override
    public Map<ID, T> findAllById(Collection<ID> keys) {
        FieldModel<T> primaryKey = this.repositoryModel.getPrimaryKey();

        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }

        if (keys.size() == 1) {
            ID next = keys.iterator().next();
            return Collections.singletonMap(next, findById(next));
        }

        SelectQuery query = Query.select().where(primaryKey.name()).eq(keys).build();

        List<T> ts = queryExecutor.executeQueryWithParams(engine.parseSelect(query, false), query, false, query.filters());
        Map<ID, T> result = new HashMap<>(ts.size());
        return this.cacheManager.addResultAndAddToCache(ts, result);
    }

    @Override
    public @NotNull CloseableIterator<T> findIterator(SelectQuery q) {
        return iteratorBuilder.findIterator(q);
    }

    @Override
    public @NotNull Stream<T> findStream(SelectQuery q) {
        return iteratorBuilder.findStream(q);
    }

    private T loadFromDatabase(ID key) {
        FieldModel<T> primaryKey = this.repositoryModel.getPrimaryKey();
        return first(Query.select().where(primaryKey.name()).eq(key).build());
    }

    @Override
    public @Nullable T first(final SelectQuery q) {
        String query = engine.parseSelect(q, true);
        List<T> results = queryExecutor.executeQueryWithParams(query, q, true, q.filters());
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public TransactionResult<Boolean> insert(@NotNull T value, TransactionContext<Connection> transactionContext) {
        return writeExecutor.executeInsertAndSetId(transactionContext, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> insertAll(Collection<T> value, TransactionContext<Connection> transactionContext) {
        if (value.isEmpty()) return TransactionResult.success(false);
        return writeExecutor.executeBatch(transactionContext, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> insertAll(@NotNull Collection<T> collection) {
        if (collection.isEmpty()) return TransactionResult.success(false);
        return writeExecutor.executeBatch(null, engine.parseInsert(), collection);
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity, TransactionContext<Connection> transactionContext) {
        ID id = this.objectModel.getId(entity);
        String sql = engine.parseUpdateFromEntity();
        TransactionResult<Boolean> result = writeExecutor.executeUpdate(
            transactionContext, sql,
            statement -> {
                SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
                this.parameterBinder.setUpdateParameters(parameters, entity, repositoryModel, resolverRegistry);
            },
            entity, id, this::findById
        );
        
        cacheManager.invalidateEntity(result, id);
        return result;
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull T entity, TransactionContext<Connection> transactionContext) {
        return writeExecutor.executeDeleteEntity(transactionContext, engine.parseDelete(entity), entity);
    }

    @Override
    public TransactionResult<Boolean> delete(T value) {
        return writeExecutor.executeDeleteEntity(null, engine.parseDelete(value), value);
    }

    @Override
    public TransactionResult<Boolean> deleteById(@NotNull ID entity, TransactionContext<Connection> transactionContext) {
        return writeExecutor.executeDeleteWithId(transactionContext, engine.parseDelete(entity), entity, this::findById);
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        return writeExecutor.executeDeleteWithId(null, engine.parseDelete(value), value, this::findById);
    }

    @Override
    public TransactionResult<Boolean> createIndex(IndexOptions index) {
        return queryExecutor.executeRawQuery(engine.parseIndex(index));
    }

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
       return queryExecutor.executeRawQuery(engine.parseRepository(ifNotExists)).flatMap((result) -> {
            for (IndexModel index : repositoryModel.indexes()) {
                TransactionResult<Boolean> indexResult = queryExecutor.executeRawQuery(engine.parseIndex(IndexOptions.builder(repository)
                        .indexName(index.name()).rawFields(index.fields()).type(index.type()).build()));
                if (indexResult.isError()) return indexResult;
            }
            return TransactionResult.success(true);
        });
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
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
            this.parameterBinder.addFilterToPreparedStatement(query.filters(), parameters, resolverRegistry, repositoryModel, sqlType);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultMapper.extractIds(resultSet, collectionHandler, supportsArrays);
            }
        } catch (Exception e) {
            return this.exceptionHandler.handleReadIds(e, repositoryModel, query, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        String sql = engine.parseUpdate(query);
        return writeExecutor.executeUpdateQuery(transactionContext, sql, query);
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query, TransactionContext<Connection> transactionContext) {
        return writeExecutor.executeDeleteQuery(transactionContext, engine.parseDelete(query), query);
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        String sql = engine.parseUpdate(query);
        return writeExecutor.executeUpdateQuery(null, sql, query);
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        return writeExecutor.executeDeleteQuery(null, engine.parseDelete(query), query);
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
        return queryExecutor.executeRawQuery("DELETE FROM " + repositoryModel.tableName());
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        return writeExecutor.executeInsertAndSetId(null, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreUpdate(entity);
        T oldEntity = null;
        if (auditLogger != null) oldEntity = findById(this.objectModel.getId(entity));

        String sql = engine.parseUpdateFromEntity();
        TransactionResult<Boolean> result = writeExecutor.executeUpdate(
            null, sql,
            statement -> {
                SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
                this.parameterBinder.setUpdateParameters(parameters, entity, repositoryModel, resolverRegistry);
            },
            entity, this.objectModel.getId(entity), this::findById
        );
        if (result.isSuccess()) {
            if (entityLifecycleListener != null) entityLifecycleListener.onPostUpdate(entity);
            if (auditLogger != null) auditLogger.onUpdate(oldEntity, entity);
        }
        return result;
    }

    @Override
    public @NotNull Class<ID> getIdType() {
        return idClass;
    }

    @Override
    public Class<T> getElementType() {
        return repository;
    }

    @Override
    @NotNull
    public OperationContext<T, ID, Connection> getOperationContext() {
        return operationContext;
    }

    @Override
    @NotNull
    public OperationExecutor<T, ID, Connection> getOperationExecutor() {
        return operationExecutor;
    }

    @Override
    @NotNull
    public TypeResolverRegistry getTypeResolverRegistry() {
        return resolverRegistry;
    }

    public SqlQueryExecutor<T, ID> getQueryExecutor() {
        return queryExecutor;
    }

    public @NotNull RepositoryModel<T, ID> getRepositoryModel() {
        return repositoryModel;
    }
}