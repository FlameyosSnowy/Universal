package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;

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
    private final TypeResolver<T> elementResolver;
    private final TypeResolver<ID> idResolver;
    private final SQLConnectionProvider connectionProvider;
    private final RepositoryModel<?, ID> information;
    private final TypeResolverRegistry resolverRegistry;
    private final String tableName;
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;

    public CollectionTypeResolver(Class<ID> idType, @NotNull Class<T> elementType,
                                  SQLConnectionProvider connectionProvider,
                                  @NotNull RepositoryModel<?, ID> information,
                                  TypeResolverRegistry resolverRegistry,
                                  CollectionHandler collectionHandler, boolean supportsArrays) {
        this.elementType = elementType;
        this.connectionProvider = connectionProvider;
        this.information = information;
        this.resolverRegistry = resolverRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;

        this.tableName = information.tableName() + '_' + elementType.getSimpleName().toLowerCase() + 's';
        this.elementResolver = resolverRegistry.resolve(elementType);
        if (elementResolver == null) throw new IllegalStateException("No resolver for " + elementType.getSimpleName());

        this.idResolver = resolverRegistry.resolve(idType);
        if (idResolver == null) throw new IllegalStateException("No resolver for primary key " + idType.getSimpleName());
    }

    public <C extends Collection<T>> C resolve(ID id, CollectionKind kind) {
        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);

            try (var rs = stmt.executeQuery()) {
                @SuppressWarnings("unchecked")
                C collection = (C) kind.create(rs.getFetchSize());

                SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry, collectionHandler, supportsArrays, information);
                while (rs.next()) {
                    collection.add(elementResolver.resolve(result, "value"));
                }
                return collection;
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public T[] resolveArray(ID id) {
        FieldModel<?> primaryKey = information.getPrimaryKey();
        String query = "SELECT * FROM " + tableName + " WHERE id = ?;";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(stmt, resolverRegistry, query, information, collectionHandler, supportsArrays);
            idResolver.insert(parameters, primaryKey.name(), id);

            try (ResultSet resultSet = stmt.executeQuery()) {
                SQLDatabaseResult databaseResult = new SQLDatabaseResult(resultSet, resolverRegistry, collectionHandler, supportsArrays, information);
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

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information, collectionHandler, supportsArrays);
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
            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);
            elementResolver.insert(params, "value", element);
            stmt.executeUpdate();
        }
    }

    public void deleteAll(ID id) throws Exception {
        String query = "DELETE FROM " + tableName + " WHERE id = ?;";
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, query, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);
            stmt.executeUpdate();
        }
    }
}