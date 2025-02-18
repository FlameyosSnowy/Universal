package io.github.flameyossnowy.universal.sql.resolvers;

import io.github.flameyossnowy.universal.sql.ObjectTypeFactory;
import io.github.flameyossnowy.universal.api.connection.ConnectionProvider;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import io.github.flameyossnowy.universal.sql.QueryParseEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.sql.*;

/**
 * This is a class that resolves collections into a table of these values.
 * @param <T> The type of the element which is stored
 * @param <ID> The parent type of what holds this collection
 */
@SuppressWarnings("unchecked")
public class RepositoryCollectionValueTypeResolver<T, ID> implements CollectionTypeResolver<T, ID> {
    private final Class<T> elementType;
    private final String tableName;

    private final ConnectionProvider<Connection> connectionProvider;

    private final ValueTypeResolverRegistry registry;
    private final RepositoryInformation metadata;
    private final QueryParseEngine engine;
    private final FieldData<?> primaryKeyType;

    public RepositoryCollectionValueTypeResolver(Class<?> parentType, String fieldName, Class<T> elementType,
                                                 final ConnectionProvider<Connection> connectionProvider,
                                                 final ValueTypeResolverRegistry registry,
                                                 final QueryParseEngine.SQLType type) {
        this.elementType = elementType;
        this.connectionProvider = connectionProvider;
        this.registry = registry;

        this.metadata = RepositoryMetadata.getMetadata(elementType);

        RepositoryInformation parentMetadata = RepositoryMetadata.getMetadata(parentType);
        this.primaryKeyType = parentMetadata.primary();

        this.engine = new QueryParseEngine(type, metadata, registry, null);
        this.tableName = parentType.getSimpleName().toLowerCase() + "_" + fieldName.toLowerCase();

        ensureTableExists();
    }

    private void ensureTableExists() {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(engine.parseRepository(true))) {
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create collection table: " + tableName, e);
        }
    }

    public Collection<T> resolve(ID id) {
        SQLValueTypeResolver<Object> typeResolver = (SQLValueTypeResolver<Object>) registry.getResolver(primaryKeyType.type());
        if (typeResolver == null) {
            throw new IllegalStateException("No resolver for " + elementType.getSimpleName() + "'s primary key!");
        }

        String query = engine.parseSelect(Query.select().where("id", id).build());
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            typeResolver.insert(stmt, 1, id);

            ResultSet resultSet = stmt.executeQuery();

            List<T> collection = new ArrayList<>(resultSet.getFetchSize());
            while (resultSet.next()) {
                collection.add(ObjectTypeFactory.create(elementType, resultSet, this::resolveField));
            }
            return collection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void insert(ID id, @NotNull Collection<T> collection) throws Exception {
        String insertQuery = engine.parseInsert(metadata.fields());
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
            for (T value : collection) {
                addElement(value, insertStmt);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        }
    }

    public void insert(ID id, T value) throws Exception {
        String insertQuery = engine.parseInsert(metadata.fields());
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
            addElement(value, insertStmt);
            insertStmt.executeUpdate();
        }
    }

    @Override
    public void delete(final ID id, final T value) throws Exception {
        String query = engine.parseDelete(null, value);
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            SQLValueTypeResolver<Object> typeResolver = (SQLValueTypeResolver<Object>) registry.getResolver(primaryKeyType.type());
            typeResolver.insert(stmt, 1, id);
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(final ID id) throws Exception {
        String query = engine.parseDelete(Query.delete().where("id", id).build());
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            SQLValueTypeResolver<Object> typeResolver = (SQLValueTypeResolver<Object>) registry.getResolver(primaryKeyType.type());
            typeResolver.insert(stmt, 1, id);
            stmt.executeUpdate();
        }
    }

    @Override
    public Class<T> getElementType() {
        return (Class<T>) metadata.type();
    }

    private void addElement(final T value, final PreparedStatement insertStmt) throws Exception {
        int index = 1;
        for (FieldData<?> data : metadata.fields()) {
            SQLValueTypeResolver<Object> resolver = (SQLValueTypeResolver<Object>) registry.getResolver(data.type());
            Object resolvedValue = getDefaultValue(value, data);

            ValueTypeResolverRegistry.EncodedTypeInfo type = registry.getEncodedType(resolver);
            if (resolvedValue == null) {
                insertStmt.setNull(index, type.sqlType());
            } else {
                resolver.insert(insertStmt, index, resolvedValue);
            }

            index++;
        }
    }

    private static <T, D> @Nullable D getDefaultValue(final T value, final @NotNull FieldData<?> data) {
        Object val = ReflectiveMetaData.getFieldValue(value, data);
        if (val == null) {
            if (data.defaultValue() != null) val = data.defaultValue();
            else return null;
        }
        return (D) val;
    }

    private Object resolveField(ResultSet resultSet, @NotNull FieldData<?> field) {
        try {
            return registry.getResolver(field.type()).resolve(resultSet, field.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

