package io.github.flameyossnowy.universal.sqlite;

import io.github.flameyossnowy.universal.api.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.repository.RepositoryMetadata;

import io.github.flameyossnowy.universal.sqlite.resolvers.ValueTypeResolverRegistry;
import io.github.flameyossnowy.universal.sqlite.connections.SimpleTransactionContext;
import io.github.flameyossnowy.universal.sqlite.resolvers.SQLiteValueTypeResolver;
import me.sunlan.fastreflection.FastField;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@SuppressWarnings({ "unchecked", "unused" })
public class SQLiteRepositoryAdapter<T> implements RepositoryAdapter<T, Connection> {
    private final ConnectionProvider<Connection> dataSource;
    private final ValueTypeResolverRegistry resolverRegistry = new ValueTypeResolverRegistry();
    private final QueryParseEngine engine;
    private final Class<T> repository;

    SQLiteRepositoryAdapter(@NotNull ConnectionProvider<Connection> dataSource, final QueryParseEngine engine, final Class<T> repository) {
        this.engine = engine;
        this.repository = repository;
        this.dataSource = dataSource;
    }

    @Contract("_ -> new")
    public static @NotNull <T> SQLiteRepositoryAdapterBuilder<T> builder(Class<T> repository) {
        return new SQLiteRepositoryAdapterBuilder<>(repository);
    }

    @Override
    public void close() throws Exception {
        dataSource.close();
    }

    @Override
    public List<T> find(final SelectQuery query) {
        String sql = engine.parseSelect(query, repository);

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {

            setStatementParameters(query, statement);

            var resultSet = statement.executeQuery();
            return mapResults(resultSet);
        } catch (Exception e) {
            throw new RuntimeException("Error executing fetch query", e);
        }
    }

    private void setStatementParameters(final SelectQuery query, final PreparedStatement statement) throws Exception {
        if (query == null || query.filters().isEmpty()) return;
        int index = 1;
        for (SelectOption value : query.filters()) {
            SQLiteValueTypeResolver<Object> resolver = (SQLiteValueTypeResolver<Object>) resolverRegistry.getResolver(value.value().getClass());
            resolver.insert(statement, index, value.value());
        }
    }

    private @NotNull List<T> mapResults(@NotNull ResultSet resultSet) throws SQLException {
        List<T> result = new ArrayList<>();
        while (resultSet.next()) result.add(build(resultSet));
        return result;
    }

    @Override
    public List<T> find() {
        return this.find(null);
    }

    @Override
    public void insert(final @NotNull T value, TransactionContext<Connection> transactionContext) {
        RepositoryMetadata.RepositoryInformation information = RepositoryMetadata.getMetadata(value.getClass());
        try (var statement = transactionContext.connection().prepareStatement(engine.parseInsert(information, information.fields()))) {
            int index = 1;
            for (RepositoryMetadata.FieldData<?> data : information.fields()) {
                processValue0(ReflectiveMetaData.getFieldValue(value, data.field()), (SQLiteValueTypeResolver<Object>) this.resolverRegistry.getResolver(data.type()), statement, index);
                index++;
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error executing insert query", e);
        }
    }

    @Override
    public void insertAll(final @NotNull Collection<T> collection, final TransactionContext<Connection> transactionContext) {
        if (collection.isEmpty()) return;

        Class<?> first = collection.iterator().next().getClass();
        RepositoryMetadata.RepositoryInformation information = RepositoryMetadata.getMetadata(first);

        try (var statement = transactionContext.connection().prepareStatement(engine.parseInsert(information, information.fields()))) {
            for (T value : collection) {
                RepositoryMetadata.RepositoryInformation val = RepositoryMetadata.getMetadata(value.getClass());
                processValue(value, val, statement);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception e) {
            throw new RuntimeException("Error executing insert query", e);
        }
    }

    private void processValue(final T value, final RepositoryMetadata.@NotNull RepositoryInformation val, final PreparedStatement statement) throws Exception {
        int index = 1;
        for (RepositoryMetadata.FieldData<?> data : val.fields()) {
            processValue0(ReflectiveMetaData.getFieldValue(value, data.field()),
                    (SQLiteValueTypeResolver<Object>) this.resolverRegistry.getResolver(data.type()), statement, index);
            index++;
        }
    }

    private void processValue0(final Object collection, final @NotNull SQLiteValueTypeResolver<Object> resolverRegistry, final PreparedStatement statement, final int index) throws Exception {
        resolverRegistry.insert(statement, index, collection);
    }

    @Override
    public void updateAll(final @NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        try (var statement = transactionContext.connection().prepareStatement(engine.parseUpdate(query, repository))) {

            setStatementParameters(statement, query.updates(), query.conditions());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error executing update query", e);
        }
    }

    private void setStatementParameters(PreparedStatement statement, @NotNull Map<String, Object> updates, List<SelectOption> conditions) throws SQLException {
        int index = 1;
        for (Object value : updates.values()) statement.setObject(index++, value);

        if (conditions == null) return;
        for (SelectOption value : conditions) statement.setObject(index++, value.value());
    }

    @Override
    public void delete(final @NotNull DeleteQuery query, TransactionContext<Connection> transactionContext) {
        String deleteQuery;
        if (query.filters().isEmpty()) deleteQuery = String.format("DELETE FROM %s", RepositoryMetadata.getMetadata(repository).repository());
        else deleteQuery = generateQuery(query, repository);

        try (var statement = transactionContext.connection().prepareStatement(deleteQuery)) {
            statement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> @NotNull String generateQuery(final @NotNull DeleteQuery query, final Class<T> repository) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption options : query.filters()) joiner.add(options.value() + " " + options.operator() + " ?");
        return String.format("DELETE FROM %s %s", RepositoryMetadata.getMetadata(repository).repository(), "WHERE " + joiner);
    }

    @Override
    public void createRepository() {
        RepositoryMetadata.RepositoryInformation metadata = RepositoryMetadata.getMetadata(repository);

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(engine.parseRepository(metadata, this))) {
            statement.execute();

            processIndexes(metadata, connection);
        } catch (Exception e) {
            throw new RuntimeException("Error creating table: " + e.getMessage(), e);
        }
    }

    private void processIndexes(final RepositoryMetadata.RepositoryInformation metadata, final Connection connection) throws SQLException {
        for (String index : engine.generateIndexes(metadata)) try (var indexStatement = connection.prepareStatement(index)) {
            indexStatement.execute();
        }
    }

    @Override
    public TransactionContext<Connection> beginTransaction() throws Exception {
        return new SimpleTransactionContext(this.dataSource.getConnection());
    }

    @Override
    public void clear() {
        try (var connection = dataSource.getConnection()) {
            var statement = connection.prepareStatement("DELETE FROM " + RepositoryMetadata.getMetadata(repository).repository());
            statement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public T build(ResultSet set) {
        RepositoryMetadata.RepositoryInformation info = RepositoryMetadata.getMetadata(repository);
        T instance = (T) ReflectiveMetaData.newInstance(info);

        try {
            buildFields(set, instance);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return instance;
    }

    private void buildFields(final ResultSet set, final T instance) throws Throwable {
        RepositoryMetadata.RepositoryInformation information = RepositoryMetadata.getMetadata(repository);
        Collection<RepositoryMetadata.FieldData<?>> data = information.fields();
        for (RepositoryMetadata.FieldData<?> entry : data) {
            String name = entry.name();
            FastField field = entry.field();

            Class<?> type = entry.rawField().getType();
            SQLiteValueTypeResolver<Object> resolver =
                    (SQLiteValueTypeResolver<Object>) this.resolverRegistry.getResolver(type);

            Object value = resolver.resolve(set, name);
            if (value != null) field.set(instance, value);
        }
    }

    public ValueTypeResolverRegistry getValueTypeResolverRegistry() {
        return resolverRegistry;
    }
}