package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.*;

@SuppressWarnings("unused")
public class CollectionTypeResolver<T, ID> {
    public static final Object[] OBJECTS = new Object[0];
    private final Class<T> elementType;
    private final Class<ID> idType;
    private final String tableName;

    private final TypeResolver<T> elementResolver;
    private final TypeResolver<ID> idResolver;

    private final SQLConnectionProvider connectionProvider;
    private final RepositoryInformation information;
    
    private final TypeResolverRegistry resolverRegistry;

    public CollectionTypeResolver(Class<ID> idType, @NotNull Class<T> elementType,
                                  final SQLConnectionProvider connectionProvider,
                                  final @NotNull RepositoryInformation information, 
                                  TypeResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
        this.idType = idType;
        this.elementType = elementType;

        this.connectionProvider = connectionProvider;
        this.information = information;

        this.tableName = information.getRepositoryName() + '_' + elementType.getSimpleName().toLowerCase() + 's';
        this.elementResolver = resolverRegistry.resolve(elementType);
        if (elementResolver == null)
            throw new IllegalStateException("No resolver for " + elementType.getSimpleName() + '.');

        this.idResolver = resolverRegistry.resolve(idType);
        if (idResolver == null)
            throw new IllegalStateException("No resolver for " + elementType.getSimpleName() + "'s primary key!");
        ensureTableExists();
    }

    private void ensureTableExists() {
        String query = "CREATE TABLE IF NOT EXISTS " + tableName + " (\n    id " + resolverRegistry.getType(idType) + " NOT NULL,\n    value " + resolverRegistry.getType(elementType) + " NOT NULL,\n    FOREIGN KEY (id) REFERENCES " + information.getRepositoryName() + " (id)\n);\n";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, conn)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create collection table: " + tableName, e);
        }
    }

    public Collection<T> resolve(ID id) {
        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query);
            idResolver.insert(parameters, "id", id);

            ResultSet resultSet = stmt.executeQuery();
            SQLDatabaseResult result = new SQLDatabaseResult(resultSet, resolverRegistry);

            List<T> collection = new ArrayList<>(resultSet.getFetchSize());
            while (resultSet.next()) {
                collection.add(elementResolver.resolve(result, "value"));
            }

            return collection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public T[] resolveArray(ID id) {
        FieldData<?> primaryKey = information.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + information.getRepositoryName());
        }

        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query);
            idResolver.insert(parameters, primaryKey.name(), id);

            ResultSet resultSet = stmt.executeQuery();
            if (!resultSet.next()) return (T[]) OBJECTS;

            SQLDatabaseResult databaseResult = new SQLDatabaseResult(resultSet, resolverRegistry);

            resultSet.last();
            int size = resultSet.getRow();
            resultSet.beforeFirst();

            @SuppressWarnings("unchecked")
            T[] array = (T[]) new Object[size];

            while (resultSet.next()) {
                int index = resultSet.getRow() - 1;
                array[index] = elementResolver.resolve(databaseResult, "value");
            }

            return array;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<T> resolveSet(ID id) {
        FieldData<?> primaryKey = information.getPrimaryKey();
        if (primaryKey == null) {
            throw new IllegalArgumentException("Primary key not found for " + information.getRepositoryName());
        }

        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query);
            idResolver.insert(parameters, primaryKey.name(), id);

            ResultSet resultSet = stmt.executeQuery();
            if (!resultSet.next()) return Set.of();

            SQLDatabaseResult databaseResult = new SQLDatabaseResult(resultSet, resolverRegistry);

            Set<T> collection = new HashSet<>(resultSet.getFetchSize());
            while (resultSet.next()) {
                collection.add(elementResolver.resolve(databaseResult, "value"));
            }

            return collection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Collection<T> collection) throws Exception {
        String insertQuery = "INSERT INTO " + tableName + " (id, value) VALUES (?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(insertStmt, resolverRegistry, insertQuery);
            batchAdd(id, collection, insertStmt, parameters);
            insertStmt.executeBatch();
        }
    }

    private void batchAdd(ID id, @NotNull Collection<T> collection, PreparedStatement insertStmt, SQLDatabaseParameters parameters) throws Exception {
        for (T value : collection) {
            addElement(value, id, insertStmt, parameters);
            insertStmt.addBatch();
        }
    }

    public void insert(ID id, T value) throws Exception {
        String insertQuery = "INSERT INTO " + tableName + " (id, value) VALUES (?, ?)";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(insertStmt, resolverRegistry);
            addElement(value, id, insertStmt, parameters);
            insertStmt.executeUpdate();
        }
    }

    public void delete(final ID id, final T value) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ? AND value = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query);
            TypeResolver<ID> typeResolver = resolverRegistry.resolve(idType);
            typeResolver.insert(parameters, "id", id);

            TypeResolver<T> resolver = resolverRegistry.resolve(elementType);
            resolver.insert(parameters, "value", value);
            stmt.executeUpdate();
        }
    }

    public void delete(final ID id) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry,query);
            TypeResolver<ID> typeResolver = resolverRegistry.resolve(idType);
            typeResolver.insert(parameters, "id", id);
            stmt.executeUpdate();
        }
    }

    public Class<T> getElementType() {
        return elementType;
    }

    private void addElement(final T value, final ID id, final PreparedStatement insertStmt, SQLDatabaseParameters parameters) {

        TypeResolver<ID> primaryKeyResolver = resolverRegistry.resolve(idType);
        primaryKeyResolver.insert(parameters, "id", id);

        TypeResolver<T> resolver = resolverRegistry.resolve(elementType);
        resolver.insert(parameters, "value", value);
    }
}
