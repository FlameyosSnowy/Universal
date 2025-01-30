package me.flame.universal.mysql;

import me.flame.universal.api.ReflectiveMetaData;
import me.flame.universal.api.RepositoryAdapter;
import me.flame.universal.api.connection.ConnectionProvider;
import me.flame.universal.api.connection.TransactionContext;
import me.flame.universal.api.options.DeleteQuery;
import me.flame.universal.api.options.SelectOption;
import me.flame.universal.api.options.SelectQuery;
import me.flame.universal.api.options.UpdateQuery;
import me.flame.universal.api.repository.RepositoryMetadata;

import me.flame.universal.mysql.connections.SimpleConnectionProvider;
import me.flame.universal.mysql.connections.SimpleTransactionContext;
import me.flame.universal.mysql.credentials.MySQLCredentials;
import me.flame.universal.mysql.query.InsertQueryParser;
import me.flame.universal.mysql.query.RepositoryParser;
import me.flame.universal.mysql.query.SelectQueryParser;
import me.flame.universal.mysql.query.UpdateQueryParser;
import me.flame.universal.mysql.resolvers.ValueTypeResolver;
import me.flame.universal.mysql.resolvers.ValueTypeResolverRegistry;

import me.sunlan.fastreflection.FastField;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MySQLRepositoryAdapter<T> implements AutoCloseable, RepositoryAdapter<T, Connection> {
    private final ConnectionProvider<Connection> dataSource;
    private final ValueTypeResolverRegistry resolverRegistry = new ValueTypeResolverRegistry();
    private final Class<T> repository;

    private MySQLRepositoryAdapter(ConnectionProvider<Connection> connectionProvider, final Class<T> repository) {
        this.repository = repository;
        this.dataSource = connectionProvider;
    }

    @Contract("_, _ -> new")
    public static @NotNull <T> MySQLRepositoryAdapter<T> open(ConnectionProvider<Connection> connectionProvider, Class<T> repository) {
        return new MySQLRepositoryAdapter<>(connectionProvider, repository);
    }

    @Contract("_, _ -> new")
    public static @NotNull <T> MySQLRepositoryAdapter<T> open(MySQLCredentials credentials, Class<T> repository) {
        return new MySQLRepositoryAdapter<>(new SimpleConnectionProvider(credentials), repository);
    }

    @Contract("_ -> new")
    public static @NotNull <T> MySQLRepositoryAdapterBuilder<T> builder(Class<T> repository) {
        return new MySQLRepositoryAdapterBuilder<>(repository);
    }

    @Override
    public void close() throws Exception {
        dataSource.close();
    }

    @Override
    public List<T> find(final SelectQuery query) {
        String sql = SelectQueryParser.parse(query, repository);

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {

            setStatementParameters(query, statement);

            var resultSet = statement.executeQuery();
            return mapResults(resultSet);
        } catch (Exception e) {
            throw new RuntimeException("Error executing fetch query", e);
        }
    }

    private static void setStatementParameters(final SelectQuery query, final PreparedStatement statement) throws SQLException {
        if (query == null || query.filters().isEmpty()) return;
        int index = 1;
        for (SelectOption value : query.filters()) {
            statement.setObject(index++, value.value());
        }
    }

    @Override
    public List<T> find() {
        return this.find(null);
    }

    private @NotNull List<T> mapResults(@NotNull ResultSet resultSet) throws SQLException {
        List<T> result = new ArrayList<>();
        while (resultSet.next()) result.add(build(resultSet));
        return result;
    }

    public void insert(final @NotNull T value, TransactionContext<Connection> transactionContext) {
        RepositoryMetadata.RepositoryInformation information = RepositoryMetadata.getMetadata(value.getClass());
        try (var statement = transactionContext.connection().prepareStatement(InsertQueryParser.parse(information, information.fields()))) {
            int index = 1;
            for (RepositoryMetadata.FieldData data : information.fields()) {
                Object fieldValue = ReflectiveMetaData.getFieldValue(value, data.field());

                ValueTypeResolver resolver = this.resolverRegistry.getResolver(data.type());
                resolver.insert(statement, index, fieldValue);
                index++;
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Error executing insert query", e);
        }
    }

    @Override
    public void insertAll(final Collection<T> collection, final TransactionContext<Connection> transactionContext) {
        if (collection.isEmpty()) return;

        Class<?> first = collection.iterator().next().getClass();
        RepositoryMetadata.RepositoryInformation information = RepositoryMetadata.getMetadata(first);
        try (var statement = transactionContext.connection().prepareStatement(InsertQueryParser.parse(information, information.fields()))) {
            for (T value : collection) {
                processValue(value, statement);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception e) {
            throw new RuntimeException("Error executing insert query", e);
        }
    }

    private void processValue(final T value, final PreparedStatement statement) throws Exception {
        int index = 1;
        RepositoryMetadata.RepositoryInformation information = RepositoryMetadata.getMetadata(value.getClass());
        for (RepositoryMetadata.FieldData data : information.fields()) {
            Object fieldValue = ReflectiveMetaData.getFieldValue(value, data.field());

            ValueTypeResolver resolver = this.resolverRegistry.getResolver(data.type());
            resolver.insert(statement, index, fieldValue);
            index++;
        }
    }

    @Override
    public void updateAll(final @NotNull UpdateQuery query, TransactionContext<Connection> transactionContext) {
        try (var statement = transactionContext.connection().prepareStatement(UpdateQueryParser.parse(query, repository))) {

            setStatementParameters(statement, query.getUpdates(), query.getConditions());
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

        try (var statement = transactionContext.connection().prepareStatement(deleteQuery);) {
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
             var statement = connection.prepareStatement(RepositoryParser.read(metadata, this))) {
            statement.execute();
        } catch (Exception e) {
            throw new RuntimeException("Error creating table: " + e.getMessage(), e);
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
        Collection<RepositoryMetadata.FieldData> data = information.fields();
        for (RepositoryMetadata.FieldData entry : data) {
            String name = entry.name();
            FastField field = entry.field();

            Class<?> type = entry.rawField().getType();
            ValueTypeResolver resolver = this.resolverRegistry.getResolver(type);

            Object value = resolver.resolve(set, name);
            if (value != null) field.set(instance, value);
        }
    }

    public ValueTypeResolverRegistry getValueTypeResolverRegistry() {
        return resolverRegistry;
    }
}