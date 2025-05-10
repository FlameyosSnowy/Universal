package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.Index;
import io.github.flameyossnowy.universal.api.cache.Session;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.exceptions.handler.DefaultExceptionHandler;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.*;

import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import io.github.flameyossnowy.universal.sql.SimpleTransactionContext;
import io.github.flameyossnowy.universal.sql.resolvers.SQLValueTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

@SuppressWarnings("unchecked")
public class AbstractRelationalRepositoryAdapter<T, ID> implements RelationalRepositoryAdapter<T, ID> {
    protected final SQLConnectionProvider dataSource;
    protected final ExceptionHandler<T, ID, Connection> exceptionHandler;
    protected final Class<T> repository;
    protected final Class<ID> idClass;
    protected final ResultCache<T, ID> cache;
    protected final SessionCache<ID, T> globalCache;
    protected final LongFunction<SessionCache<ID, T>> sessionCacheSupplier;
    protected final RepositoryInformation repositoryInformation;
    protected SQLObjectFactory<T, ID> objectFactory;
    protected final QueryParseEngine engine;

    protected long openedSessions = 1;

    static final Map<Class<?>, RelationalRepositoryAdapter<?, ?>> ADAPTERS = new ConcurrentHashMap<>(3);

    protected AbstractRelationalRepositoryAdapter(SQLConnectionProvider dataSource, ResultCache<T, ID> cache, @NotNull Class<T> repository, Class<ID> idClass, QueryParseEngine.SQLType type, SessionCache<ID, T> globalCache, LongFunction<SessionCache<ID, T>> sessionCacheSupplier) {
        this.sessionCacheSupplier = sessionCacheSupplier;
        Logging.info("Initializing repository: " + repository.getSimpleName());
        this.globalCache = globalCache;

        this.repositoryInformation = RepositoryMetadata.getMetadata(repository);
        if (repositoryInformation == null)
            throw new IllegalArgumentException("Could not find repository information for class: " + repository.getSimpleName());
        ADAPTERS.put(repository, this);
        Logging.info("Repository information: " + repositoryInformation);

        this.idClass = idClass;

        this.dataSource = dataSource;
        this.cache = cache;
        this.repository = repository;

        this.objectFactory = new ObjectFactory<>(repositoryInformation, dataSource, this);

        ExceptionHandler<T, ID, Connection> exceptionHandler = (ExceptionHandler<T, ID, Connection>) repositoryInformation.getExceptionHandler();
        this.exceptionHandler = exceptionHandler == null ? new DefaultExceptionHandler<>() : exceptionHandler;

        Logging.info("Creating QueryParseEngine for query generation for table " + repositoryInformation.getRepositoryName() + '.');
        this.engine = new QueryParseEngine(type, repositoryInformation);
        Logging.info("Successfully created QueryParseEngine for table: " + repositoryInformation.getRepositoryName());
    }

    protected void setObjectFactory(SQLObjectFactory<T, ID> objectFactory) {
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

    private @NotNull List<T> search(String query, boolean first, @NotNull List<SelectOption> filters) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(query, connection)) {

            int index = 1;
            for (SelectOption value : filters) {
                if (value == null) continue;
                SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(value.value().getClass());
                resolver.insert(statement, index++, value.value());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                return first ? fetchFirst(query, resultSet) : fetchAll(query, resultSet);
            }
        }
    }

    private @NotNull List<T> fetchAll(String query, ResultSet resultSet) throws Exception {
        if (repositoryInformation.getFetchPageSize() > 0) resultSet.setFetchSize(repositoryInformation.getFetchPageSize());
        return insertToCache(query, repositoryInformation.hasRelationships()
                ? mapResultsWithRelationships(query, resultSet, new ArrayList<>(resultSet.getFetchSize()))
                : mapResults(query, resultSet, new ArrayList<>(resultSet.getFetchSize())));
    }

    private @NotNull List<T> fetchFirst(String query, @NotNull ResultSet resultSet) throws Exception {
        if (!resultSet.next()) return insertToCache(query, List.of());
        return insertToCache(query, List.of(repositoryInformation.hasRelationships() ? this.objectFactory.create(resultSet) : this.objectFactory.createWithRelationships(resultSet)));
    }

    @Override
    public List<T> find() {
        return executeQuery(engine.parseSelect(null, false));
    }

    @Override
    public T findById(ID key) {
        return first(Query.select().where(repositoryInformation.getPrimaryKey().name(), key).build());
    }

    @Override
    public T first(final SelectQuery q) {
        String query = engine.parseSelect(q, true);
        return executeQueryWithParams(query, true, q.filters()).get(0);
    }

    @Override
    public TransactionResult<Boolean> insert(@NotNull T value, TransactionContext<Connection> transactionContext) {
        return executeInsertAndSetId(transactionContext, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> insertAll(List<T> value, TransactionContext<Connection> transactionContext) {
        if (value.isEmpty()) return null;
        executeBatch(transactionContext, engine.parseInsert(), value);
        return null;
    }

    @Override
    public TransactionResult<Boolean> insertAll(@NotNull List<T> collection) {
        if (collection.isEmpty()) return null;
        executeBatch(null, engine.parseInsert(), collection);
        return null;
    }

    private void executeBatch(TransactionContext<Connection> transactionContext, String sql, List<T> collection) {
        try (Connection connection = transactionContext == null ? dataSource.getConnection() : transactionContext.connection();
             PreparedStatement statement = dataSource.prepareStatement(sql, connection)) {

            connection.setAutoCommit(false);
            try {
                for (T entity : collection) {
                    objectFactory.insertEntity(statement, entity);
                    statement.addBatch();
                }

                statement.executeBatch();
                connection.commit();

                for (T entity : collection) {
                    objectFactory.insertCollectionEntities(entity, repositoryInformation.getPrimaryKey().getValue(entity), statement);
                }

                if (cache != null) cache.clear();
            } catch (Exception e) {
                connection.rollback();
                this.exceptionHandler.handleInsert(e, repositoryInformation, this);
            }
        } catch (Exception e) {
            this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }


    @Override
    public TransactionResult<Boolean> updateAll(@NotNull T entity, TransactionContext<Connection> transactionContext) {
        return executeUpdate(transactionContext, engine.parseUpdateFromEntity(), statement -> this.setUpdateParameters(statement, entity), entity);
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
        return executeDeleteWithId(transactionContext, engine.parseDelete(entity), entity);
    }

    @Override
    public TransactionResult<Boolean> deleteById(ID value) {
        return executeDeleteWithId(null, engine.parseDelete(value), value);
    }

    @Override
    public TransactionResult<Boolean> createIndex(IndexOptions index) {
        executeRawQuery(engine.parseIndex(index));
        return null;
    }

    @Override
    public TransactionResult<Boolean> createRepository(boolean ifNotExists) {
        executeRawQuery(engine.parseRepository(ifNotExists));
        for (Index index : repositoryInformation.getIndexes())
            executeRawQuery(engine.parseIndex(IndexOptions.builder(repository).indexName(index.name()).fields(index.fields()).type(index.type()).build()));
        return TransactionResult.success(true);
    }

    private @NotNull List<T> mapResults(String query, @NotNull ResultSet resultSet, List<T> results) throws Exception {
        while (resultSet.next()) {
            T entity = this.objectFactory.create(resultSet);
            if (globalCache != null) globalCache.put(repositoryInformation.getPrimaryKey().getValue(entity), entity);
            results.add(entity);
        }
        return insertToCache(query, results);
    }

    private List<T> insertToCache(String query, List<T> result) {
        if (cache != null) cache.insert(query, result);
        return result;
    }

    private @NotNull List<T> mapResultsWithRelationships(String query, @NotNull ResultSet resultSet, List<T> fetchedData) throws Exception {
        while (resultSet.next()) {
            T entity = this.objectFactory.createWithRelationships(resultSet);
            if (globalCache != null) globalCache.put(repositoryInformation.getPrimaryKey().getValue(entity), entity);
            fetchedData.add(entity);
        }
        return insertToCache(query, fetchedData);
    }

    @Override
    public TransactionContext<Connection> beginTransaction() {
        try {
            return new SimpleTransactionContext(dataSource.getConnection());
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage());
        }
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        return executeUpdate(transactionContext, engine.parseUpdate(query), statement -> AbstractRelationalRepositoryAdapter.setUpdateParameters(statement, query));
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query, TransactionContext<Connection> transactionContext) {
        return executeDelete(transactionContext, engine.parseDelete(query), query);
    }

    @Override
    public TransactionResult<Boolean> updateAll(@NotNull UpdateQuery query) {
        return executeUpdate(null, engine.parseUpdate(query), statement -> AbstractRelationalRepositoryAdapter.setUpdateParameters(statement, query));
    }

    @Override
    public TransactionResult<Boolean> delete(@NotNull DeleteQuery query) {
        return executeDelete(null, engine.parseDelete(query), query);
    }

    @Override
    public Session<ID, T, Connection> createSession() {
        openedSessions++;
        return new SQLSession<>(this, sessionCacheSupplier.apply(openedSessions), openedSessions);
    }

    @Override
    public TransactionResult<Boolean> clear() {
        executeRawQuery("DELETE FROM " + repositoryInformation.getRepositoryName());
        return null;
    }

    @Override
    public <A> A createDynamicProxy(@NotNull Class<A> adapter) {
        return (A) Proxy.newProxyInstance(adapter.getClassLoader(), new Class[]{adapter}, new ProxiedAdapterHandler<>(this));
    }

    @Override
    public TransactionResult<Boolean> insert(T value) {
        return executeInsertAndSetId(null, engine.parseInsert(), value);
    }

    @Override
    public TransactionResult<Boolean> updateAll(T entity) {
        return executeUpdate(null, engine.parseUpdateFromEntity(), statement -> setUpdateParameters(statement, entity));
    }

    @Override
    public Class<ID> getIdType() {
        return idClass;
    }

    @Override
    public RepositoryInformation getInformation() {
        return repositoryInformation;
    }

    @Override
    public Class<T> getElementType() {
        return repository;
    }

    @Override
    public void executeRawQuery(final String query) {
        Logging.info("Parsed query: " + query);
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = dataSource.prepareStatement(query, connection)) {
            statement.execute();
        } catch (Exception e) {
            this.exceptionHandler.handleInsert(e, repositoryInformation, this);
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

    @Override
    public SQLObjectFactory<T, ID> getObjectFactory() {
        return objectFactory;
    }

    private TransactionResult<Boolean> executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter, T entity) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            if (globalCache != null) globalCache.put(repositoryInformation.getPrimaryKey().getValue(entity), entity);

            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }


    private TransactionResult<Boolean> executeDelete(TransactionContext<Connection> transactionContext, String sql, T entity) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            ID id = repositoryInformation.getPrimaryKey().getValue(entity);

            SQLValueTypeResolver<ID> resolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idClass);
            resolver.insert(statement, 1, id);

            if (cache != null) cache.clear();
            if (globalCache != null) globalCache.put(id, entity);

            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeDelete(TransactionContext<Connection> transactionContext, String sql, DeleteQuery query) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            setUpdateParameters(statement, query);
            if (cache != null) cache.clear();
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeDeleteWithId(TransactionContext<Connection> transactionContext, String sql, ID id) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            SQLValueTypeResolver<ID> resolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idClass);
            resolver.insert(statement, 1, id);

            if (cache != null) cache.clear();
            if (globalCache != null) globalCache.remove(id);

            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryInformation, this);
        }
    }

    private TransactionResult<Boolean> executeInsertAndSetId(TransactionContext<Connection> transactionContext, String sql, T value) {
        try (Connection connection = transactionContext == null ? dataSource.getConnection() : transactionContext.connection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            this.objectFactory.insertEntity(statement, value);

            if (statement.executeUpdate() > 0) {
                if (repositoryInformation.getPrimaryKey() == null) {
                    return TransactionResult.success(true);
                }
                if (!repositoryInformation.getPrimaryKey().autoIncrement()) {
                    this.objectFactory.insertCollectionEntities(value, repositoryInformation.getPrimaryKey().getValue(value), statement);
                    if (globalCache != null) globalCache.put(repositoryInformation.getPrimaryKey().getValue(value), value);
                    return TransactionResult.success(true);
                }
                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    SQLValueTypeResolver<ID> resolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idClass);
                    ID generatedId = resolver.resolve(generatedKeys, repositoryInformation.getPrimaryKey().name());

                    repositoryInformation.getPrimaryKey().setValue(value, generatedId);

                    if (globalCache != null) globalCache.put(generatedId, value);

                    this.objectFactory.insertCollectionEntities(value, generatedId, statement);
                }
                if (cache != null) cache.clear();
                return TransactionResult.success(true);
            }
            return TransactionResult.success(false);
        } catch (Exception exception) {
            return this.exceptionHandler.handleInsert(exception, repositoryInformation, this);
        }
    }

    private void setUpdateParameters(PreparedStatement statement, @NotNull T entity) throws Exception {
        int index = 1;
        for (FieldData<?> fieldData : repositoryInformation.getFields()) {
            Object value = fieldData.getValue(entity);

            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(fieldData.type());
            resolver.insert(statement, index, value);
        }
    }

    private static void setUpdateParameters(PreparedStatement statement, @NotNull UpdateQuery query) throws Exception {
        int index = 1;
        for (Object value : query.updates().values()) {
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(value.getClass());
            resolver.insert(statement, index, value);
        }

        List<SelectOption> conditions = query.conditions();
        if (conditions.isEmpty()) return;
        for (SelectOption value : conditions) {
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(value.value().getClass());
            resolver.insert(statement, index, value);
        }
    }

    private static void setUpdateParameters(PreparedStatement statement, @NotNull DeleteQuery query) throws Exception {
        int index = 1;
        for (SelectOption value : query.filters()) {
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(value.value().getClass());
            resolver.insert(statement, index, value.value());
        }
    }

    private interface StatementSetter {
        void set(PreparedStatement statement) throws Exception;
    }
}