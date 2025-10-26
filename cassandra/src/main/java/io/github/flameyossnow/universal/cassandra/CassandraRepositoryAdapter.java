package io.github.flameyossnow.universal.cassandra;


import com.datastax.driver.core.*;
import io.github.flameyossnow.universal.cassandra.factory.CassandraObjectFactory;
import io.github.flameyossnow.universal.cassandra.handler.CassandraRelationshipHandler;
import io.github.flameyossnow.universal.cassandra.objects.CassandraDatabaseParameters;
import io.github.flameyossnow.universal.cassandra.query.CassandraQueryEngine;
import io.github.flameyossnow.universal.cassandra.query.CassandraTableParser;
import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType;
import io.github.flameyossnowy.universal.api.cache.*;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.handler.DefaultExceptionHandler;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

@SuppressWarnings("unchecked")
public class CassandraRepositoryAdapter<T, ID> implements RepositoryAdapter<T, ID, Session> {
    private final RepositoryInformation repositoryInformation;
    private final ExceptionHandler<T, ID, Session> exceptionHandler;
    private final DefaultResultCache<String, T, ID> resultCache;
    // Advanced caches
    private final SecondLevelCache<ID, T> l2Cache;
    private final ReadThroughCache<ID, T> readThroughCache;
    private final Class<T> repository;
    private final Class<ID> idClass;
    private final SessionCache<ID, T> globalCache;
    private final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;
    private final CassandraTableParser tableParser;
    private final CassandraObjectFactory<T, ID> objectFactory;
    private final Cluster cluster;
    private final Session session;
    private final CassandraQueryEngine engine;
    private final TypeResolverRegistry resolverRegistry = new TypeResolverRegistry();
    private int currentSession = 0;

    private static final Map<Class<?>, RepositoryAdapter<?, ?, ?>> ADAPTERS = new ConcurrentHashMap<>();

    private final AuditLogger<T> auditLogger;
    private final EntityLifecycleListener<T> entityLifecycleListener;

    // PreparedStatement cache for performance optimization
    private final Map<String, PreparedStatement> preparedStatementCache = new ConcurrentHashMap<>();

    public CassandraRepositoryAdapter(
            DefaultResultCache<String, T, ID> resultCache,
            Class<T> repository,
            Class<ID> idClass,
            SessionCache<ID, T> globalCache,
            LongFunction<SessionCache<ID, T>> sessionCacheSupplier,
            CassandraCredentials credentials,
            CacheWarmer<T, ID> cacheWarmer) {
        this.resultCache = resultCache;
        this.repository = repository;
        this.idClass = idClass;
        this.globalCache = globalCache;
        this.sessionCacheSupplier = sessionCacheSupplier;

        if (cacheWarmer != null) {
            cacheWarmer.warmCache(this);
        }

        Logging.info("Initializing repository: " + repository.getSimpleName());

        Cluster.Builder clusterBuilder = Cluster.builder().addContactPoint(credentials.node());

        if (credentials.port() != -1) {
            clusterBuilder.withPort(credentials.port());
        }

        // Apply pooling options for better performance
        if (credentials.poolingOptions() != null) {
            clusterBuilder.withPoolingOptions(credentials.poolingOptions());
        } else {
            clusterBuilder.withPoolingOptions(CassandraCredentials.defaultPoolingOptions());
        }

        // Apply retry policy if provided
        if (credentials.retryPolicy() != null) {
            clusterBuilder.withRetryPolicy(credentials.retryPolicy());
        }

        // Apply load balancing policy if provided
        if (credentials.loadBalancingPolicy() != null) {
            clusterBuilder.withLoadBalancingPolicy(credentials.loadBalancingPolicy());
        }

        this.cluster = clusterBuilder.build();

        // Connect to specific keyspace if provided, otherwise connect without keyspace
        this.session = credentials.keyspace() != null
                ? this.cluster.connect(credentials.keyspace())
                : this.cluster.connect();

        this.repositoryInformation = RepositoryMetadata.getMetadata(repository);
        if (repositoryInformation == null)
            throw new IllegalArgumentException("Could not find repository information for class: " + repository.getSimpleName());

        Logging.info("Repository information: " + repositoryInformation);

        this.objectFactory = new CassandraObjectFactory<>(
                repositoryInformation,
                resolverRegistry,
                idClass,
                repository,
                session,
                new CassandraRelationshipHandler<>(repositoryInformation, idClass, ADAPTERS, resolverRegistry)
        );

        @SuppressWarnings("unchecked")
        ExceptionHandler<T, ID, Session> exceptionHandler = (ExceptionHandler<T, ID, Session>) repositoryInformation.getExceptionHandler();

        this.exceptionHandler = exceptionHandler == null ? new DefaultExceptionHandler<>() : exceptionHandler;
        this.tableParser = new CassandraTableParser(this.repositoryInformation, this.resolverRegistry);
        this.engine = new CassandraQueryEngine(this.repositoryInformation);

        // Initialize advanced caches
        this.l2Cache = new SecondLevelCache<>(10000, 300000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED);
        this.readThroughCache = new ReadThroughCache<>(10000, CacheAlgorithmType.LEAST_FREQ_AND_RECENTLY_USED, this::loadFromDatabase);

        entityLifecycleListener = (EntityLifecycleListener<T>) repositoryInformation.getEntityLifecycleListener();
        auditLogger = (AuditLogger<T>) repositoryInformation.getAuditLogger();
    }

    private T loadFromDatabase(ID id) {
        return first(Query.select().where(repositoryInformation.getPrimaryKey().name(), id).build());
    }

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
        this.session.execute(this.tableParser.parseRepository(ifNotExists));
        return TransactionResult.success(true);
    }

    @Override
    public TransactionContext<Session> beginTransaction() {
        return new CassandraTransactionContext(this.session);
    }

    @Override
    public DatabaseSession<ID, T, Session> createSession() {
        return createSession(EnumSet.noneOf(SessionOption.class));
    }

    @Override
    public DatabaseSession<ID, T, Session> createSession(EnumSet<SessionOption> options) {
        currentSession++;
        return new DefaultSession<>(this, sessionCacheSupplier.apply(0), currentSession, options);
    }

    @Override
    public List<T> find(SelectQuery query) {
        String sql = this.engine.parseSelect(query, query.limit() == 1);
        return fetchAllResults(sql, query);
    }

    @Override
    public List<T> find() {
        String sql = this.engine.parseSelect(null, false);
        return fetchAllResults(sql, null);
    }

    private List<T> fetchAllResults(String sql, SelectQuery query) {
        if (resultCache != null) {
            List<T> result = this.resultCache.fetch(sql);
            if (result != null) return result;
        }

        BoundStatement bind = insertParametersIntoQuery(sql, query);
        ResultSet resultSet = this.session.execute(bind);
        List<Row> all = resultSet.all();
        try {
            List<T> results = new ArrayList<>(all.size());

            for (Row row : all) {
                T instance = objectFactory.create(row);
                results.add(instance);
            }

            if (resultCache != null) this.resultCache.insert(sql, results, (element) -> repositoryInformation.getPrimaryKey().getValue(element));
            return results;
        } catch (Exception e) {
            return this.exceptionHandler.handleRead(e, repositoryInformation, query, this);
        }
    }

    private BoundStatement insertParametersIntoQuery(String sql, SelectQuery query) {
        PreparedStatement statement = getPreparedStatement(sql);
        CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
        for (SelectOption value : query.filters()) {
            if (value == null) continue;

            // Handle IN clause with list of values
            if ("IN".equalsIgnoreCase(value.operator()) && value.value() instanceof List<?> list) {
                for (Object item : list) {
                    TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(item.getClass());
                    resolver.insert(parameters, value.option(), item);
                }
            } else {
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(value.value().getClass());
                resolver.insert(parameters, value.option(), value.value());
            }
        }

        return statement.bind(parameters.getValues());
    }

    /**
     * Gets a cached PreparedStatement or prepares and caches a new one.
     * This significantly improves performance by avoiding repeated query preparation.
     */
    private PreparedStatement getPreparedStatement(String cql) {
        return preparedStatementCache.computeIfAbsent(cql, session::prepare);
    }

    @Override
    public T findById(ID key) {
        T cached = l2Cache.get(key);
        if (cached != null) {
            Logging.deepInfo("L2 cache hit for " + repositoryInformation.getRepositoryName() + " id=" + key);
            return cached;
        }

        // Read-through cache next
        T value = readThroughCache.get(key);
        if (value != null) {
            l2Cache.put(key, value);
            return value;
        }

        return null;
    }

    @Override
    public T first(SelectQuery query) {
        Objects.requireNonNull(query);
        String sql = this.engine.parseSelect(query, false);
        return fetchOneResult(sql, query);
    }

    private T fetchOneResult(String sql, SelectQuery query) {
        if (resultCache != null) {
            List<T> result = this.resultCache.fetch(sql);
            if (result != null) return result.get(0);
        }

        BoundStatement bind = insertParametersIntoQuery(sql, query);
        ResultSet resultSet = this.session.execute(bind);

        Row one = resultSet.one();
        try {
            List<T> results = List.of(objectFactory.create(one));

            if (resultCache != null) this.resultCache.insert(sql, results, (element) -> repositoryInformation.getPrimaryKey().getValue(element));
            return results.get(0);
        } catch (Exception e) {
            return this.exceptionHandler.handleRead(e, repositoryInformation, query, this).get(0);
        }
    }

    @Override
    public TransactionResult<Boolean> insert(T value, TransactionContext<Session> _transactionContext) {
        Objects.requireNonNull(value);
        String sql = this.engine.parseInsert();

        try {
            PreparedStatement statement = getPreparedStatement(sql);
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
            ID id = repositoryInformation.getPrimaryKey().getValue(value);
            this.objectFactory.insertEntity(parameters, value);
            this.objectFactory.insertCollectionEntities(value, id, statement);
            ResultSet resultSet = this.session.execute(statement.bind(parameters.getValues()));

            if (globalCache != null) globalCache.put(id, value);
            if (resultCache != null) resultCache.clear(); // Clear cache after write
            // Invalidate key-based caches
            if (id != null) {
                l2Cache.invalidate(id);
                readThroughCache.invalidate(id);
            }
            return TransactionResult.success(resultSet.wasApplied());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity, TransactionContext<Session> _transactionContext) {
        Objects.requireNonNull(entity);
        String sql = this.engine.parseUpdateFromEntity();

        try {
            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreUpdate(entity);
            }

            ID id = repositoryInformation.getPrimaryKey().getValue(entity);

            T oldEntity = null;
            if (auditLogger != null) {
                oldEntity = findById(id);
            }

            PreparedStatement statement = getPreparedStatement(sql);
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
            this.objectFactory.insertEntity(parameters, entity);
            this.objectFactory.insertCollectionEntities(entity, id, statement);
            ResultSet resultSet = this.session.execute(statement.bind(parameters.getValues()));
            boolean wasApplied = resultSet.wasApplied();

            if (wasApplied) {
                // Notify audit logger after successful update
                if (auditLogger != null) {
                    auditLogger.onUpdate(entity, oldEntity);
                }

                // Update caches
                if (resultCache != null) resultCache.clear();
                if (globalCache != null) {
                    if (id != null) {
                        globalCache.put(id, entity);
                        l2Cache.invalidate(id);
                        readThroughCache.invalidate(id);
                    }
                }
            }

            return TransactionResult.success(wasApplied);
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(T entity, TransactionContext<Session> _transactionContext) {
        Objects.requireNonNull(entity);
        try {

            String cql = engine.parseDelete(entity);
            PreparedStatement statement = getPreparedStatement(cql);
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
            return processDelete(entity, statement, parameters, cql);
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    @NotNull
    private TransactionResult<Boolean> processDelete(T entity, PreparedStatement statement, CassandraDatabaseParameters parameters, String cql) {
        ID id = repositoryInformation.getPrimaryKey().getValue(entity);

        try {
            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreDelete(entity);
            }

            boolean wasApplied = executeDelete(statement, parameters, id).wasApplied();

            if (auditLogger != null) {
                auditLogger.onDelete(entity);
            }

            if (id != null) {
                l2Cache.invalidate(id);
                readThroughCache.invalidate(id);
            }

            return TransactionResult.success(wasApplied);
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    private ResultSet executeDelete(PreparedStatement statement, CassandraDatabaseParameters parameters, ID id) {
        TypeResolver<ID> resolver = resolverRegistry.getResolver(idClass);
        resolver.insert(parameters, repositoryInformation.getPrimaryKey().name(), id);

        BoundStatement bind = statement.bind(parameters.getValues());
        ResultSet resultSet = session.execute(bind);

        if (resultCache != null) resultCache.clear();
        if (globalCache != null) globalCache.remove(id);
        return resultSet;
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID entity, TransactionContext<Session> _transactionContext) {
        Objects.requireNonNull(entity);

        try {
            T existingEntity = null;
            if (auditLogger != null || entityLifecycleListener != null) {
                existingEntity = findById(entity);
                if (existingEntity == null) return TransactionResult.success(false);
            }

            if (entityLifecycleListener != null) {
                entityLifecycleListener.onPreDelete(existingEntity);
            }

            String cql = engine.parseDelete(entity);
            PreparedStatement statement = getPreparedStatement(cql);
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();

            if (repositoryInformation.hasCompositeKey()) {
                // For composite keys, we need to set all primary key fields
                for (FieldData<?> key : repositoryInformation.getPrimaryKeys()) {
                    Object value = key.getValue(entity);
                    TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(value.getClass());
                    resolver.insert(parameters, key.name(), value);
                }
            } else {
                TypeResolver<ID> resolver = resolverRegistry.getResolver(idClass);
                resolver.insert(parameters, repositoryInformation.getPrimaryKey().name(), entity);
            }

            BoundStatement bind = statement.bind(parameters.getValues());
            ResultSet resultSet = session.execute(bind);
            boolean wasApplied = resultSet.wasApplied();

            if (wasApplied) {
                if (auditLogger != null) {
                    auditLogger.onDelete(existingEntity);
                }
                
                if (resultCache != null) resultCache.clear();
                if (globalCache != null) globalCache.remove(entity);
                l2Cache.invalidate(entity);
                readThroughCache.invalidate(entity);
            }

            return TransactionResult.success(wasApplied);
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<Session> _transactionContext) {
        Objects.requireNonNull(query);

        try {
            String cql = engine.parseUpdate(query);
            PreparedStatement statement = getPreparedStatement(cql);
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();

            for (Map.Entry<String, Object> entry : query.updates().entrySet()) {
                Object value = entry.getValue();
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(value.getClass());
                resolver.insert(parameters, entry.getKey(), value);
            }

            for (SelectOption condition : query.conditions()) {
                if (condition == null) continue;
                TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(condition.value().getClass());
                resolver.insert(parameters, condition.option(), condition.value());
            }

            BoundStatement bind = statement.bind(parameters.getValues());
            ResultSet resultSet = session.execute(bind);

            if (resultCache != null) resultCache.clear();

            return TransactionResult.success(resultSet.wasApplied());
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> delete(DeleteQuery query, TransactionContext<Session> tx) {
        if (query == null || query.filters().isEmpty()) {
            throw new IllegalArgumentException("Cannot delete without filters, this is to prevent accidental deletion of all data, please use #clear() instead");
        }
        String cql = engine.parseDelete(query);
        try {
            PreparedStatement statement = getPreparedStatement(cql);
            CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
            setUpdateParameters(parameters, query);

            BoundStatement bind = statement.bind(parameters.getValues());
            ResultSet resultSet = session.execute(bind);
            if (resultCache != null) resultCache.clear();
            return TransactionResult.success(resultSet.wasApplied());
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryInformation, this);
        }
    }

    @Override
    public TransactionResult<Boolean> insertAll(List<T> value, TransactionContext<Session> _transactionContext) {
        if (value.isEmpty()) return TransactionResult.success(true);
        return executeBatch(this.engine.parseInsert(), value);
    }

    // Delegate
    // -----------------------------------------------------------------------------
    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        return updateAll(query, null);
    }

    @Override
    public TransactionResult<Boolean> delete(T value) {
        return delete(value, null);
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        return deleteById(value, null);
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        return delete(query, null);
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        return insert(value, null);
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity) {
        return updateAll(entity, null);
    }

    @Override
    public TransactionResult<Boolean> insertAll(List<T> query) {
        return insertAll(query, null);
    }
    // -----------------------------------------------------------------------------
    // End delegate

    @Override
    public TransactionResult<Boolean> clear() {
        try {
            return TransactionResult.success(session.execute("DELETE FROM " + repositoryInformation.getRepositoryName()).wasApplied());
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public TransactionResult<Boolean> createIndex(IndexOptions index) {
        try {
            return TransactionResult.success(session.execute(engine.parseIndex(index)).wasApplied());
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    @Override
    public Class<ID> getIdType() {
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
    public void close() {
        preparedStatementCache.clear();
        this.session.close();
        this.cluster.close();
    }

    public TransactionResult<Boolean> executeBatch(String cql, List<T> collection) {
        try {
            PreparedStatement prepared = getPreparedStatement(cql);

            if ( entityLifecycleListener != null) {
                for (T value : collection) {
                    entityLifecycleListener.onPreInsert(value);
                }
            }

            // Use UNLOGGED for better performance when writing to the same partition key
            // (e.g. when inserting multiple entities with the same partition key)
            // See https://docs.datastax.com/en/developer/java-driver/latest/manual/core/batch/ for more details
            // Use LOGGED only if we need atomicity across multiple partitions
            BatchStatement batch = new BatchStatement(BatchStatement.Type.UNLOGGED);

            for (T entity : collection) {
                CassandraDatabaseParameters parameters = new CassandraDatabaseParameters();
                objectFactory.insertEntity(parameters, entity);
                BoundStatement bound = prepared.bind(parameters.getValues());
                batch.add(bound);
            }

            session.execute(batch);

            if (entityLifecycleListener != null || auditLogger != null) {
                for (T value : collection) {
                    if (entityLifecycleListener != null) entityLifecycleListener.onPostDelete(value);
                    if (auditLogger != null) auditLogger.onInsert(value);
                }
            }

            if (resultCache != null) resultCache.clear();
            // Note: cannot selectively invalidate L2/RT cache for batch without IDs list

            return TransactionResult.success(true);

        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    private void setUpdateParameters(CassandraDatabaseParameters statement, @NotNull DeleteQuery query) {
        for (SelectOption value : query.filters()) {
            TypeResolver<Object> resolver = (TypeResolver<Object>) resolverRegistry.getResolver(value.value().getClass());
            resolver.insert(statement, value.option(), value.value());
        }
    }
}