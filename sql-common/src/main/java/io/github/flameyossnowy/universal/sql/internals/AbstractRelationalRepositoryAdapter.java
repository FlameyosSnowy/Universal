package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.Index;
import io.github.flameyossnowy.universal.api.cache.FetchedDataResult;
import io.github.flameyossnowy.universal.api.cache.ResultCache;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
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

@SuppressWarnings("unchecked")
public class AbstractRelationalRepositoryAdapter<T, ID> implements RelationalRepositoryAdapter<T, ID> {
    private final SQLConnectionProvider dataSource;
    private final QueryParseEngine engine;
    private final Class<T> repository;
    private final Class<ID> idClass;
    private final ResultCache<T, ID> cache;
    private final RepositoryInformation repositoryInformation;

    private final ObjectFactory<T, ID> objectFactory;

    static final Map<Class<?>, RelationalRepositoryAdapter<?, ?>> ADAPTERS = new ConcurrentHashMap<>();

    protected AbstractRelationalRepositoryAdapter(@NotNull SQLConnectionProvider dataSource, ResultCache<T, ID> cache, @NotNull Class<T> repository, Class<ID> idClass, QueryParseEngine.SQLType type) {

        Logging.info("Initializing repository: " + repository.getSimpleName());
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

        Logging.info("Creating QueryParseEngine for query generation for table " + repositoryInformation.getRepositoryName() + '.');
        this.engine = new QueryParseEngine(type, repositoryInformation);
        Logging.info("Successfully created QueryParseEngine for table: " + repositoryInformation.getRepositoryName());
    }

    @Override
    public void close() throws Exception {
        dataSource.close();
    }

    @Override
    public FetchedDataResult<T, ID> find(SelectQuery q) {
        String query = engine.parseSelect(q, false);
        return executeQueryWithParams(query, q.filters());
    }

    private @NotNull FetchedDataResult<T, ID> search(String query, boolean first, List<SelectOption> filters) throws Exception {
        ResultSet resultSet = this.executeRawQueryWithParams(query, filters);
        return first ? fetchFirst(query, resultSet) : fetchAll(query, resultSet);
    }

    private @NotNull FetchedDataResult<T, ID> fetchAll(String query, ResultSet resultSet) throws Exception {
        if (repositoryInformation.getFetchPageSize() > 0) resultSet.setFetchSize(repositoryInformation.getFetchPageSize());
        return insertToCache(query, repositoryInformation.hasRelationships()
                ? mapResultsWithRelationships(query, resultSet, new ArrayList<>())
                : mapResults(query, resultSet, new ArrayList<>()));
    }

    private @NotNull FetchedDataResult<T, ID> fetchFirst(String query, @NotNull ResultSet resultSet) throws Exception {
        if (!resultSet.next()) return insertToCache(query, FetchedDataResult.empty());
        return insertToCache(query, 
                FetchedDataResult.of(repositoryInformation.hasRelationships() ? this.objectFactory.create(resultSet) : this.objectFactory.createWithRelationships(resultSet)));
    }

    @Override
    public FetchedDataResult<T, ID> find() {
        return executeQuery(engine.parseSelect(null, false));
    }

    @Override
    public T findById(ID key) {
        return first(Query.select().where(repositoryInformation.getPrimaryKey().name(), key).build());
    }

    @Override
    public T first(final SelectQuery q) {
        String query = engine.parseSelect(q, true);
        return executeQueryWithParams(query, true, q.filters()).first();
    }

    @Override
    public boolean insert(@NotNull T value, TransactionContext<Connection> transactionContext) {
        return executeUpdate(transactionContext, engine.parseInsert(), stmt -> this.objectFactory.insertEntity(stmt, value));
    }

    @Override
    public void insertAll(@NotNull List<T> collection, TransactionContext<Connection> transactionContext) {
        if (collection.isEmpty()) return;
        executeBatch(transactionContext, engine.parseInsert(), collection);
    }

    @Override
    public boolean updateAll(@NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        return executeUpdate(transactionContext, engine.parseUpdate(query), statement -> this.setUpdateParameters(statement, query));
    }

    @Override
    public boolean delete(@NotNull DeleteQuery query, TransactionContext<Connection> transactionContext) {
        return executeUpdate(transactionContext, engine.parseDelete(query), null);
    }

    @Override
    public boolean delete(T value) {
        return executeUpdate(null, engine.parseDelete(value), null);
    }

    @Override
    public void createIndex(IndexOptions index) {
        executeRawQuery(engine.parseIndex(index));
    }

    @Override
    public void createRepository(boolean ifNotExists) {
        executeRawQuery(engine.parseRepository(ifNotExists));
        for (Index index : repositoryInformation.getIndexes())
            executeRawQuery(engine.parseIndex(IndexOptions.builder(repository).indexName(index.name()).fields(index.fields()).type(index.type()).build()));
    }

    private @NotNull FetchedDataResult<T, ID> mapResults(String query, @NotNull ResultSet resultSet, List<T> results) throws Exception {
        while (resultSet.next()) results.add(this.objectFactory.create(resultSet));
        return insertToCache(query, FetchedDataResult.of(results));
    }

    private FetchedDataResult<T, ID> insertToCache(String query, FetchedDataResult<T, ID> result) {
        if (cache != null) cache.insert(query, result);
        return result;
    }

    private @NotNull FetchedDataResult<T, ID> mapResultsWithRelationships(String query, @NotNull ResultSet resultSet, List<T> fetchedData) throws Exception {
        while (resultSet.next()) fetchedData.add(this.objectFactory.createWithRelationships(resultSet));
        return insertToCache(query, FetchedDataResult.of(fetchedData));
    }

    @Override
    public TransactionContext<Connection> beginTransaction() throws Exception {
        return new SimpleTransactionContext(dataSource.getConnection());
    }

    @Override
    public void clear() {
        executeRawQuery("DELETE FROM " + repositoryInformation.getRepositoryName());
    }

    @Override
    public <A> A createDynamicProxy(@NotNull Class<A> adapter) {
        return (A) Proxy.newProxyInstance(adapter.getClassLoader(), new Class[]{adapter}, new ProxiedAdapterHandler<>(this));
    }

    @Override
    public boolean insert(T value) {
        return executeUpdate(null, engine.parseInsert(), stmt -> this.objectFactory.insertEntity(stmt, value));
    }

    @Override
    public boolean updateAll(UpdateQuery query) {
        return executeUpdate(null, engine.parseUpdate(query), statement -> setUpdateParameters(statement, query));
    }

    @Override
    public boolean delete(DeleteQuery query) {
        return executeUpdate(null, engine.parseDelete(query), null);
    }

    @Override
    public void insertAll(@NotNull List<T> collection) {
        if (!collection.isEmpty()) executeBatch(engine.parseInsert(), collection);
    }

    @Override
    public Class<ID> getIdType() {
        return idClass;
    }

    @Override
    public void executeRawQuery(final String query) {
        Logging.info("Parsed query: " + query);
        try (var connection = dataSource.getConnection(); var statement = dataSource.prepareStatement(query, connection)) {
            statement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResultSet executeRawQuery(String query, Object... parameters) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = dataSource.prepareStatement(query, connection)) {
            int index = 1;
            for (Object value : parameters) {
                SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(value.getClass());
                resolver.insert(statement, index, value);
                index++;
            }
            return statement.executeQuery();
        } catch (Exception e) {
            Logging.error(e);
            return null;
        }
    }

    @Override
    public ResultSet executeRawQueryWithParams(String query, @NotNull List<SelectOption> parameters) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = dataSource.prepareStatement(query, connection)) {
            int index = 1;
            for (SelectOption value : parameters) {
                SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) ValueTypeResolverRegistry.INSTANCE.getResolver(value.value().getClass());
                resolver.insert(statement, index, value.value());
                index++;
            }
            return statement.executeQuery();
        }
    }

    @Override
    public FetchedDataResult<T, ID> executeQuery(String query, Object... params) {
        FetchedDataResult<T, ID> result;
        try {
            return cache != null && (result = cache.fetch(query)) != null ? result : fetchAll(query, this.executeRawQuery(query, params));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FetchedDataResult<T, ID> executeQueryWithParams(String query, List<SelectOption> params) {
        return executeQueryWithParams(query, false, params);
    }

    @Override
    public FetchedDataResult<T, ID> executeQueryWithParams(String query, boolean first, List<SelectOption> params) {
        FetchedDataResult<T, ID> result;
        try {
            return cache != null && (result = cache.fetch(query)) != null ? result : search(query, first, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            return statement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeBatch(TransactionContext<Connection> transactionContext, String sql, List<T> collection) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext.connection())) {
            for (T value : collection) {
                this.objectFactory.insertEntity(statement, value);
                statement.addBatch();
            }
            statement.executeBatch();
            if (cache != null) cache.clear();
        } catch (Exception e) { 
            throw new RuntimeException(e);
        }
    }

    private void executeBatch(String sql, List<T> collection) {
        try (var connection = dataSource.getConnection();
             var statement = dataSource.prepareStatement(sql, connection)) {
            connection.setAutoCommit(false);
            try {
                for (T value : collection) {
                    this.objectFactory.insertEntity(statement, value);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit(); // Commit if everything is successful

                if (cache != null) cache.clear();
            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException("Transaction rolled back due to an error", e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute transaction", e);
        }

    }

    private void setUpdateParameters(PreparedStatement statement, @NotNull UpdateQuery query) throws Exception {
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

    private interface StatementSetter {
        void set(PreparedStatement statement) throws Exception;
    }
}