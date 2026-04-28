package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.factory.CollectionKind;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.internals.query.ParameterizedSql;
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
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;

    private final ParameterizedSql selectSql;
    private final ParameterizedSql insertSql;
    private final ParameterizedSql deleteOneSql;
    private final ParameterizedSql deleteAllSql;

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

        this.elementResolver = resolverRegistry.resolve(elementType);
        if (elementResolver == null) throw new IllegalStateException("No resolver for " + elementType.getSimpleName());

        this.idResolver = resolverRegistry.resolve(idType);
        if (idResolver == null) throw new IllegalStateException("No resolver for primary key " + idType.getSimpleName());

        String table = information.tableName() + '_' + elementType.getSimpleName().toLowerCase() + 's';

        // Ensure join table exists
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "CREATE TABLE IF NOT EXISTS " + table + " (id TEXT NOT NULL, value TEXT NOT NULL);")) {
            stmt.execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create collection table: " + table, e);
        }

        this.selectSql    = ParameterizedSql.of("SELECT * FROM " + table + " WHERE id = ?;",               List.of("id"));
        this.insertSql    = ParameterizedSql.of("INSERT INTO " + table + " (id, value) VALUES (?, ?)",      List.of("id", "value"));
        this.deleteOneSql = ParameterizedSql.of("DELETE FROM " + table + " WHERE id = ? AND value = ?;",   List.of("id", "value"));
        this.deleteAllSql = ParameterizedSql.of("DELETE FROM " + table + " WHERE id = ?;",                 List.of("id"));
    }

    public <C extends Collection<T>> C resolve(ID id, CollectionKind kind) {
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(selectSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, selectSql, information, collectionHandler, supportsArrays);
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
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(selectSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, selectSql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);

            try (ResultSet rs = stmt.executeQuery()) {
                SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry, collectionHandler, supportsArrays, information);
                ArrayList<T> list = new ArrayList<>(Math.max(32, stmt.getFetchSize()));

                while (rs.next()) {
                    list.add(elementResolver.resolve(result, "value"));
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
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(insertSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, insertSql, information, collectionHandler, supportsArrays);
            for (T element : collection) {
                idResolver.insert(params, "id", id);
                elementResolver.insert(params, "value", element);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public void delete(ID id, T element) throws Exception {
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(deleteOneSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, deleteOneSql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);
            elementResolver.insert(params, "value", element);
            stmt.executeUpdate();
        }
    }

    public void deleteAll(ID id) throws Exception {
        try (var connection = connectionProvider.getConnection();
             var stmt = connectionProvider.prepareStatement(deleteAllSql.sql(), connection)) {

            SQLDatabaseParameters params = new SQLDatabaseParameters(stmt, resolverRegistry, deleteAllSql, information, collectionHandler, supportsArrays);
            idResolver.insert(params, "id", id);
            stmt.executeUpdate();
        }
    }
}