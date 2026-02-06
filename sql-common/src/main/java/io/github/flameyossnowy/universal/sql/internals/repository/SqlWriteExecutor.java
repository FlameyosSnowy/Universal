package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public final class SqlWriteExecutor<T, ID> {
    private static final int BATCH_SIZE = 1000;

    private final io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider dataSource;
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final io.github.flameyossnowy.universal.api.handler.CollectionHandler collectionHandler;
    private final boolean supportsArrays;
    private final DefaultResultCache<String, T, ID> cache;
    private final SessionCache<ID, T> globalCache;
    private final RelationshipHandler<T, ID> relationshipHandler;
    private final ExceptionHandler<T, ID, Connection> exceptionHandler;
    private final RepositoryAdapter<T, ID, Connection> adapter;
    private final ObjectModel<T, ID> objectModel;
    private final Class<ID> idClass;
    private final AuditLogger<T> auditLogger;
    private final EntityLifecycleListener<T> entityLifecycleListener;

    private final SqlParameterBinder<T, ID> parameterBinder;
    private final QueryParseEngine.SQLType sqlType;

    private final boolean isAutoIncrement;
    private final String[] primaryKeyColumnNames;

    public SqlWriteExecutor(
        io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider dataSource,
        RepositoryModel<T, ID> repositoryModel,
        TypeResolverRegistry resolverRegistry,
        io.github.flameyossnowy.universal.api.handler.CollectionHandler collectionHandler,
        boolean supportsArrays,
        DefaultResultCache<String, T, ID> cache,
        SessionCache<ID, T> globalCache,
        RelationshipHandler<T, ID> relationshipHandler,
        ExceptionHandler<T, ID, Connection> exceptionHandler,
        RepositoryAdapter<T, ID, Connection> adapter,
        ObjectModel<T, ID> objectModel,
        Class<ID> idClass,
        AuditLogger<T> auditLogger,
        EntityLifecycleListener<T> entityLifecycleListener,
        SqlParameterBinder<T, ID> parameterBinder,
        QueryParseEngine.SQLType sqlType,
        boolean isAutoIncrement,
        String[] primaryKeyColumnNames
    ) {
        this.dataSource = dataSource;
        this.repositoryModel = repositoryModel;
        this.resolverRegistry = resolverRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;
        this.cache = cache;
        this.globalCache = globalCache;
        this.relationshipHandler = relationshipHandler;
        this.exceptionHandler = exceptionHandler;
        this.adapter = adapter;
        this.objectModel = objectModel;
        this.idClass = idClass;
        this.auditLogger = auditLogger;
        this.entityLifecycleListener = entityLifecycleListener;
        this.parameterBinder = parameterBinder;
        this.sqlType = sqlType;
        this.isAutoIncrement = isAutoIncrement;
        this.primaryKeyColumnNames = primaryKeyColumnNames;
    }

    public TransactionResult<Boolean> executeBatch(TransactionContext<Connection> transactionContext, String sql, Collection<T> collection) {
        try (Connection connection = transactionContext == null ? dataSource.getConnection() : transactionContext.connection();
             PreparedStatement statement = dataSource.prepareStatement(sql, connection)) {

            if (transactionContext == null) connection.setAutoCommit(false);
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
            try {
                int i = 0;
                for (T entity : collection) {
                    objectModel.insertEntity(parameters, entity);
                    statement.addBatch();

                    if ((i + 1) % BATCH_SIZE == 0) {
                        statement.executeBatch();
                        statement.clearBatch();
                    }

                    i++;
                }

                if (collection.size() % BATCH_SIZE != 0) {
                    statement.executeBatch();
                }

                connection.commit();

                FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
                if (primaryKey != null) {
                    for (T entity : collection) {
                        objectModel.insertCollectionEntities(entity, objectModel.getId(entity), parameters);
                    }
                }

                if (cache != null) cache.clear();

                return TransactionResult.success(true);
            } catch (Exception e) {
                connection.rollback();
                return this.exceptionHandler.handleInsert(e, repositoryModel, adapter);
            }
        } catch (Exception e) {
            return this.exceptionHandler.handleInsert(e, repositoryModel, adapter);
        }
    }

    public TransactionResult<Boolean> executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            return TransactionResult.success(statement.execute());
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryModel, adapter);
        }
    }

    public TransactionResult<Boolean> executeUpdateQuery(TransactionContext<Connection> transactionContext, String sql, UpdateQuery query) {
        return executeUpdate(transactionContext, sql, statement -> {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
            parameterBinder.setUpdateParameters(query, parameters, resolverRegistry, repositoryModel, sqlType);
        });
    }

    public TransactionResult<Boolean> executeUpdate(TransactionContext<Connection> transactionContext, String sql, StatementSetter setter, T entity, ID id, java.util.function.Function<ID, T> findById) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreUpdate(entity);
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            if (setter != null) setter.set(statement);
            if (cache != null) cache.clear();
            if (globalCache != null) globalCache.put(id, entity);

            T oldEntity = null;
            if (auditLogger != null) oldEntity = findById.apply(id);

            TransactionResult<Boolean> success = TransactionResult.success(statement.execute());
            if (auditLogger != null) auditLogger.onUpdate(oldEntity, entity);
            if (entityLifecycleListener != null) entityLifecycleListener.onPostUpdate(entity);
            invalidateRelationships(id);
            return success;
        } catch (Exception e) {
            return this.exceptionHandler.handleUpdate(e, repositoryModel, adapter);
        }
    }

    public TransactionResult<Boolean> executeDelete(TransactionContext<Connection> transactionContext, String sql, DeleteMode mode) {
        try (var statement = dataSource.prepareStatement(sql, transactionContext == null ? dataSource.getConnection() : transactionContext.connection())) {
            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
            return mode.apply(parameters, statement);
        } catch (Exception e) {
            return this.exceptionHandler.handleDelete(e, repositoryModel, adapter);
        }
    }

    public TransactionResult<Boolean> executeDeleteQuery(TransactionContext<Connection> transactionContext, String sql, DeleteQuery query) {
        return executeDelete(transactionContext, sql, (parameters, statement) -> {
            parameterBinder.setUpdateParameters(query, parameters, resolverRegistry, repositoryModel, sqlType);
            if (cache != null) cache.clear();
            relationshipHandler.clear();
            return TransactionResult.success(statement.execute());
        });
    }

    public TransactionResult<Boolean> executeDeleteEntity(TransactionContext<Connection> transactionContext, String sql, @NotNull T entity) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreDelete(entity);
        return executeDelete(transactionContext, sql, (parameters, statement) ->
            processDelete(objectModel.getId(entity), parameters, statement, entity)
        );
    }

    public TransactionResult<Boolean> executeDeleteWithId(TransactionContext<Connection> transactionContext, String sql, @NotNull ID id, java.util.function.Function<ID, T> findById) {
        T byId = null;
        if (auditLogger != null || entityLifecycleListener != null) byId = findById.apply(id);
        if (entityLifecycleListener != null) entityLifecycleListener.onPreDelete(byId);
        T entity = byId;
        return executeDelete(transactionContext, sql, (parameters, statement) ->
            processDelete(id, parameters, statement, entity)
        );
    }

    private @NotNull TransactionResult<Boolean> processDelete(ID id, SQLDatabaseParameters parameters, PreparedStatement statement, T entity) throws SQLException {
        var resolver = resolverRegistry.resolve(idClass);
        resolver.insert(parameters, repositoryModel.getPrimaryKey().name(), id);

        if (cache != null) cache.clear();
        if (globalCache != null) globalCache.remove(id);

        TransactionResult<Boolean> success = TransactionResult.success(statement.execute());
        if (auditLogger != null) auditLogger.onDelete(entity);
        if (entityLifecycleListener != null) entityLifecycleListener.onPostDelete(entity);
        invalidateRelationships(id);
        return success;
    }

    public TransactionResult<Boolean> executeInsertAndSetId(TransactionContext<Connection> transactionContext, String sql, T value) {
        if (entityLifecycleListener != null) entityLifecycleListener.onPreInsert(value);
        try (Connection connection = transactionContext == null ? dataSource.getConnection() : transactionContext.connection();
             PreparedStatement statement = prepareStatementWithGeneratedKeys(connection, sql)) {

            SQLDatabaseParameters parameters = new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);

            this.objectModel.insertEntity(parameters, value);

            if (statement.executeUpdate() > 0) {
                if (repositoryModel.getPrimaryKey() == null) {
                    return TransactionResult.success(true);
                }

                if (repositoryModel.hasRelationships()) {
                    ID entityId = repositoryModel.getPrimaryKeyValue(value);
                    relationshipHandler.invalidateRelationshipsForId(entityId);

                    for (FieldModel<T> parentField : repositoryModel.getManyToOneCache().values()) {
                        invalidateExistingCaches(value, parentField);
                    }

                    for (FieldModel<T> parentField : repositoryModel.getOneToOneCache().values()) {
                        invalidateExistingCaches(value, parentField);
                    }
                }

                if (!repositoryModel.getPrimaryKey().autoIncrement()) {
                    this.objectModel.insertCollectionEntities(value, repositoryModel.getPrimaryKeyValue(value), parameters);
                    if (globalCache != null) globalCache.put(repositoryModel.getPrimaryKeyValue(value), value);
                    return TransactionResult.success(true);
                }

                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    SQLDatabaseResult result = new SQLDatabaseResult(generatedKeys, resolverRegistry, collectionHandler, supportsArrays, repositoryModel);
                    if (generatedKeys.next()) {
                        TypeResolver<ID> resolver = resolverRegistry.resolve(idClass);
                        ID generatedId = resolver.resolve(result, repositoryModel.getPrimaryKey().name());

                        repositoryModel.getPrimaryKey().setValue(value, generatedId);

                        if (globalCache != null) globalCache.put(generatedId, value);

                        this.objectModel.insertCollectionEntities(value, generatedId, parameters);
                    }
                    if (cache != null) cache.clear();
                    if (auditLogger != null) auditLogger.onInsert(value);
                    if (entityLifecycleListener != null) entityLifecycleListener.onPostInsert(value);

                    return TransactionResult.success(true);
                }
            }
            return TransactionResult.success(false);
        } catch (Exception exception) {
            return this.exceptionHandler.handleInsert(exception, repositoryModel, adapter);
        }
    }

    private PreparedStatement prepareStatementWithGeneratedKeys(Connection connection, String sql) throws SQLException {
        if (isAutoIncrement) {
            return connection.prepareStatement(sql, primaryKeyColumnNames);
        }

        return connection.prepareStatement(sql);
    }

    private void invalidateExistingCaches(T value, @NotNull FieldModel<T> parentField) {
        @SuppressWarnings("unchecked")
        T parentValue = (T) parentField.getValue(value);
        if (parentValue != null) {
            RepositoryModel<T, ID> parentRepositoryModel = (RepositoryModel<T, ID>) GeneratedMetadata.getByEntityClass(parentField.type());

            @SuppressWarnings("DataFlowIssue")
            ID parentId = parentRepositoryModel.getPrimaryKeyValue(parentValue);
            relationshipHandler.invalidateRelationshipsForId(parentId);
        }
    }

    private void invalidateRelationships(ID id) {
        if (!repositoryModel.hasRelationships()) return;
        if (id == null) return;

        try {
            relationshipHandler.invalidateRelationshipsForId(id);
            Logging.deepInfo("Invalidated relationship cache for ID: " + id);
        } catch (Exception e) {
            Logging.error("Failed to invalidate relationship cache for ID " + id + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface StatementSetter {
        void set(PreparedStatement statement) throws Exception;
    }

    @FunctionalInterface
    public interface DeleteMode {
        TransactionResult<Boolean> apply(SQLDatabaseParameters parameters, PreparedStatement statement) throws Exception;
    }
}