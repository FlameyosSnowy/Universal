package io.github.flameyossnowy.universal.sql;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Index;
import io.github.flameyossnowy.universal.api.cache.ResultCache;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sql.resolvers.SQLValueTypeResolver;
import io.github.flameyossnowy.universal.sql.resolvers.ValueTypeResolverRegistry;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public abstract class AbstractRelationalRepositoryAdapter<T, ID> implements RelationalRepositoryAdapter<T, ID> {
    private final ConnectionProvider<Connection> dataSource;
    private final ResultCache cache;
    private final ValueTypeResolverRegistry resolverRegistry;
    private final QueryParseEngine engine;

    private final RepositoryInformation repositoryInformation;
    private final Class<T> repository;

    protected AbstractRelationalRepositoryAdapter(@NotNull ConnectionProvider<Connection> dataSource, ResultCache cache, EnumSet<Optimizations> optimizations, Class<T> repository, QueryParseEngine.SQLType type) {
        this.dataSource = dataSource;
        this.cache = cache;
        this.resolverRegistry = new ValueTypeResolverRegistry();
        this.repositoryInformation = RepositoryMetadata.getMetadata(repository);
        this.engine = new QueryParseEngine(type, repositoryInformation, resolverRegistry, optimizations);
        this.repository = repository;
    }

    @Override
    public void close() throws Exception {
        dataSource.close();
    }

    @Override
    public List<T> find(SelectQuery query) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(engine.parseSelect(query, false))) {
            var resultSet = statement.executeQuery();
            return mapResults(query, resultSet);
        } catch (Exception e) {
            throw new RuntimeException("Error executing fetch query", e);
        }
    }

    @Override
    public List<T> find() {
        return find(null);
    }

    @Override
    public T first(final SelectQuery query) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(engine.parseSelect(query, true))) {
            var resultSet = statement.executeQuery();
            return mapResult(query, resultSet);
        } catch (Exception e) {
            throw new RuntimeException("Error executing fetch query", e);
        }
    }

    @Override
    public void insert(@NotNull T value, TransactionContext<Connection> transactionContext) {
        executeUpdate(transactionContext, engine.parseInsert(repositoryInformation.fields()), stmt -> setStatementValues(stmt, value));
    }

    @Override
    public void insertAll(@NotNull List<T> collection, TransactionContext<Connection> transactionContext) {
        if (collection.isEmpty()) return;
        executeBatch(transactionContext, engine.parseInsert(repositoryInformation.fields()), collection);
    }

    @Override
    public void updateAll(@NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        executeUpdate(transactionContext, engine.parseUpdate(query), statement -> setUpdateParameters(statement, query));
    }

    @Override
    public void delete(@NotNull DeleteQuery query, TransactionContext<Connection> transactionContext) {
        executeUpdate(transactionContext, engine.parseDelete(query), null);
    }

    @Override
    public void createIndex(IndexOptions index) {
        executeStatement(engine.parseIndex(index));
    }

    @Override
    public void createRepository(boolean ifNotExists) {
        executeStatement(engine.parseRepository(ifNotExists));
        for (Index index : repositoryInformation.indexes()) executeStatement(engine.parseIndex(IndexOptions.builder(repository)
                .indexName(index.name())
                .fields(index.fields())
                .type(index.type()).build()));
    }

    private @NotNull List<T> mapResults(Query query, @NotNull ResultSet resultSet) throws SQLException {
        List<T> results = new ArrayList<>(resultSet.getFetchSize());
        while (resultSet.next()) results.add(ObjectTypeFactory.create(repository, resultSet, this::resolveField));
        if (!results.isEmpty() && cache != null) cache.refresh(query, results);
        return results;
    }

    private @NotNull T mapResult(Query query, @NotNull ResultSet resultSet) {
        return ObjectTypeFactory.create(repository, resultSet, this::resolveField);
    }

    private Object resolveField(ResultSet resultSet, @NotNull FieldData<?> field) {
        try {
            return resolverRegistry.getResolver(field.type()).resolve(resultSet, field.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TransactionContext<Connection> beginTransaction() throws Exception {
        return new SimpleTransactionContext(dataSource.getConnection());
    }

    @Override
    public void clear() {
        executeStatement("DELETE FROM " + repositoryInformation.repository());
    }

    @Override
    public void executeRawQuery(final String query) {
        executeStatement(query);
    }

    public ValueTypeResolverRegistry getValueTypeResolverRegistry() {
        return resolverRegistry;
    }

    private void executeStatement(String sql) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter) {
        try (var statement = transactionContext.connection().prepareStatement(sql)) {
            if (setter != null) setter.set(statement);
            statement.executeUpdate();
            if (cache != null) cache.clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void executeBatch(TransactionContext<Connection> transactionContext, String sql, Collection<T> collection) {
        try (var statement = transactionContext.connection().prepareStatement(sql)) {
            for (T value : collection) {
                setStatementValues(statement, value);
                statement.addBatch();
            }
            statement.executeBatch();
            if (cache != null) cache.clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void setStatementValues(PreparedStatement statement, T value) throws Exception {
        int index = 1;
        for (FieldData<?> data : repositoryInformation.fields()) {
            resolverRegistry.getResolver(data.type()).insert(statement, index++, ReflectiveMetaData.getFieldValue(value, data));
        }
    }

    private void setUpdateParameters(PreparedStatement statement, UpdateQuery query) throws Exception {
        int index = 1;
        for (Object value : query.updates().values()) {
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) resolverRegistry.getResolver(value.getClass());
            resolver.insert(statement, index, value);
        }

        if (query.conditions() == null || query.conditions().isEmpty()) return;
        for (SelectOption value : query.conditions()) {
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) resolverRegistry.getResolver(value.value().getClass());
            resolver.insert(statement, index, value);
        }
    }

    private interface StatementSetter {
        void set(PreparedStatement statement) throws Exception;
    }
}