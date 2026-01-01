package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.*;

@SuppressWarnings("unused")
public class CollectionTypeResolver<T, ID> {
    private static final Object[] OBJECTS = new Object[0];
    private final Class<T> elementType;
    private final Class<ID> idType;
    private final TypeResolver<T> elementResolver;
    private final TypeResolver<ID> idResolver;
    private final SQLConnectionProvider connectionProvider;
    private final RepositoryInformation information;
    private final TypeResolverRegistry resolverRegistry;
    private final String tableName;

    public CollectionTypeResolver(Class<ID> idType, @NotNull Class<T> elementType,
                                  SQLConnectionProvider connectionProvider,
                                  @NotNull RepositoryInformation information,
                                  TypeResolverRegistry resolverRegistry) {
        this.idType = idType;
        this.elementType = elementType;
        this.connectionProvider = connectionProvider;
        this.information = information;
        this.resolverRegistry = resolverRegistry;

        this.tableName = information.getRepositoryName() + '_' + elementType.getSimpleName().toLowerCase() + 's';
        this.elementResolver = resolverRegistry.resolve(elementType);
        if (elementResolver == null) throw new IllegalStateException("No resolver for " + elementType.getSimpleName());

        this.idResolver = resolverRegistry.resolve(idType);
        if (idResolver == null) throw new IllegalStateException("No resolver for primary key " + idType.getSimpleName());
    }

    public <C extends Collection<T>> C resolve(ID id, CollectionKind kind) {
        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(params, "id", id);

            C collection;
            try (var rs = stmt.executeQuery()) {
                collection = (C) kind.create(rs.getFetchSize());

                SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry);
                while (rs.next()) {
                    collection.add(elementResolver.resolve(result, "value"));
                }
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
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(parameters, primaryKey.name(), id);

            try (ResultSet resultSet = stmt.executeQuery()) {
                SQLDatabaseResult databaseResult = new SQLDatabaseResult(resultSet, resolverRegistry);
                ArrayList<T> list = new ArrayList<>(Math.max(32, stmt.getFetchSize()));

                while (resultSet.next()) {
                    int index = resultSet.getRow() - 1;
                    list.add(elementResolver.resolve(databaseResult, "value"));
                }

                @SuppressWarnings("unchecked")
                T[] arr = (T[]) Array.newInstance(elementType, list.size());
                return list.toArray(arr);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Collection<T> collection) throws Exception {
        batchInsert(id, collection);
    }

    private void batchInsert(ID id, @NotNull Collection<T> collection) throws Exception {
        String query = "INSERT INTO " + tableName + " (id, value) VALUES (?, ?)";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            for (T element : collection) {
                idResolver.insert(params, "id", id);
                elementResolver.insert(params, "value", element);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public void delete(ID id, T element) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ? AND value = ?;";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(params, "id", id);
            elementResolver.insert(params, "value", element);
            stmt.executeUpdate();
        }
    }

    public void deleteAll(ID id) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ?;";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information);
            idResolver.insert(params, "id", id);
            stmt.executeUpdate();
        }
    }
}