package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public class NormalCollectionTypeResolver<T, ID> {
    private final Class<T> elementType;
    private final Class<ID> idType;
    private final String tableName;

    private final SQLValueTypeResolver<T> elementResolver;
    private final SQLValueTypeResolver<ID> idResolver;

    private final SQLConnectionProvider connectionProvider;
    private final RepositoryInformation information;

    public NormalCollectionTypeResolver(Class<ID> idType, Class<T> elementType,
                                        final SQLConnectionProvider connectionProvider,
                                        final RepositoryInformation information) {
        this.idType = idType;
        this.elementType = elementType;

        this.connectionProvider = connectionProvider;
        this.information = information;

        this.tableName = information.getRepositoryName() + '_' + elementType.getSimpleName().toLowerCase() + 's';
        this.elementResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(elementType);
        if (elementResolver == null)
            throw new IllegalStateException("No resolver for " + elementType.getSimpleName() + ".");

        this.idResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idType);
        if (idResolver == null)
            throw new IllegalStateException("No resolver for " + elementType.getSimpleName() + "'s primary key!");
        ensureTableExists();
    }

    private void ensureTableExists() {
        String query = String.format("""
        CREATE TABLE IF NOT EXISTS %s (
            id %s NOT NULL,
            value %s NOT NULL,
            FOREIGN KEY (id) REFERENCES %s (id)
        );
        """, tableName, ValueTypeResolverRegistry.INSTANCE.getType(idType), ValueTypeResolverRegistry.INSTANCE.getType(elementType), information.getRepositoryName());

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, conn)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create collection table: " + tableName, e);
        }
    }

    public Collection<T> resolve(ID id) {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement("SELECT * FROM " + tableName + " WHERE id = ?;", connection)) {

            idResolver.insert(stmt, 1, id);

            ResultSet resultSet = stmt.executeQuery();

            List<T> collection = new ArrayList<>(resultSet.getFetchSize());
            while (resultSet.next()) {
                collection.add(elementResolver.resolve(resultSet, "value"));
            }

            return collection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Collection<T> collection) throws Exception {
        String insertQuery = String.format("INSERT INTO %s (id, value) VALUES (?, ?)", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            batchAdd(id, collection, insertStmt);
            insertStmt.executeBatch();
        }
    }

    private void batchAdd(ID id, @NotNull Collection<T> collection, PreparedStatement insertStmt) throws Exception {
        for (T value : collection) {
            addElement(value, id, insertStmt);
            insertStmt.addBatch();
        }
    }

    public void insert(ID id, T value) throws Exception {
        String insertQuery = String.format("INSERT INTO %s (id, value) VALUES (?, ?)", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connectionProvider.prepareStatement(insertQuery, connection)) {
            addElement(value, id, insertStmt);
            insertStmt.executeUpdate();
        }
    }

    public void delete(final ID id, final T value) throws Exception {
        String query = String.format("DELETE FROM %s WHERE id = ? AND value = ?;", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLValueTypeResolver<ID> typeResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idType);
            typeResolver.insert(stmt, 1, id);

            SQLValueTypeResolver<T> resolver = ValueTypeResolverRegistry.INSTANCE.getResolver(elementType);
            resolver.insert(stmt, 2, value);
            stmt.executeUpdate();
        }
    }

    public void delete(final ID id) throws Exception {
        String query = String.format("DELETE FROM %s WHERE id = ?;", tableName);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connectionProvider.prepareStatement(query, connection)) {
            SQLValueTypeResolver<ID> typeResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idType);
            typeResolver.insert(stmt, 1, id);
            stmt.executeUpdate();
        }
    }

    public Class<T> getElementType() {
        return elementType;
    }

    private void addElement(final T value, final ID id, final PreparedStatement insertStmt) throws Exception {
        SQLValueTypeResolver<ID> primaryKeyResolver = ValueTypeResolverRegistry.INSTANCE.getResolver(idType);
        primaryKeyResolver.insert(insertStmt, 1, id);

        SQLValueTypeResolver<T> resolver = ValueTypeResolverRegistry.INSTANCE.getResolver(elementType);
        resolver.insert(insertStmt, 2, value);
    }
}
