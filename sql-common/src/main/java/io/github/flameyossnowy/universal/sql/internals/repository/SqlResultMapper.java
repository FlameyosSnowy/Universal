package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedValueReaders;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SqlResultMapper<T, ID> {

    private final RepositoryModel<T, ID> repositoryModel;
    private final Class<ID> idClass;
    private final TypeResolverRegistry resolverRegistry;
    private final ObjectModel<T, ID> objectModel;
    private final RelationshipLoader<T, ID> relationshipLoader;
    private final SessionCache<ID, T> globalCache;
    private final DefaultResultCache<String, T, ID> cache;

    public SqlResultMapper(
        RepositoryModel<T, ID> repositoryModel,
        Class<ID> idClass,
        TypeResolverRegistry resolverRegistry,
        ObjectModel<T, ID> objectModel,
        RelationshipLoader<T, ID> relationshipLoader,
        SessionCache<ID, T> globalCache,
        DefaultResultCache<String, T, ID> cache
    ) {
        this.repositoryModel = repositoryModel;
        this.idClass = idClass;
        this.resolverRegistry = resolverRegistry;
        this.objectModel = objectModel;
        this.relationshipLoader = relationshipLoader;
        this.globalCache = globalCache;
        this.cache = cache;
    }

    public RepositoryModel<T, ID> getRepositoryModel() {
        return repositoryModel;
    }

    public TypeResolverRegistry getResolverRegistry() {
        return resolverRegistry;
    }

    public int getFetchSizeOrDefault() {
        int fetchSize = repositoryModel.getFetchPageSize();
        return fetchSize > 0 ? fetchSize : 100;
    }

    public SQLDatabaseParameters createParameters(
        java.sql.PreparedStatement statement,
        String sql,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) {
        return new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);
    }

    public SQLDatabaseResult createDatabaseResult(
        ResultSet resultSet,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) {
        return new SQLDatabaseResult(resultSet, resolverRegistry, collectionHandler, supportsArrays, repositoryModel);
    }

    public @NotNull List<ID> extractIds(
        ResultSet rs,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) throws SQLException {
        FieldModel<T> primaryKey = repositoryModel.getPrimaryKey();
        String idColumn = primaryKey.name();
        TypeResolver<ID> resolver = resolverRegistry.resolve(idClass);

        List<ID> list = new ArrayList<>(32);

        SQLDatabaseResult result = new SQLDatabaseResult(rs, resolverRegistry, collectionHandler, supportsArrays, repositoryModel);
        while (rs.next()) {
            ID id = resolver.resolve(result, idColumn);
            list.add(id);
        }

        return list;
    }

    public T constructNewEntity(SQLDatabaseResult databaseResult) {
        ID id = resolverRegistry.resolve(idClass).resolve(databaseResult, repositoryModel.getPrimaryKey().columnName());
        if (repositoryModel.hasRelationships()) {
            T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, resolverRegistry, id));
            objectModel.populateRelationships(construct, id, relationshipLoader);
            return construct;
        }
        return objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, resolverRegistry, id));
    }

    public List<T> mapResults(
        String query,
        @NotNull ResultSet resultSet,
        List<T> results,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) throws Exception {
        boolean existingGlobalCache = globalCache != null;

        SQLDatabaseResult databaseResult = new SQLDatabaseResult(resultSet, resolverRegistry, collectionHandler, supportsArrays, repositoryModel);

        while (resultSet.next()) {
            ID id = resolverRegistry.resolve(idClass).resolve(databaseResult, repositoryModel.getPrimaryKey().columnName());
            T entity = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, resolverRegistry, id));
            objectModel.populateRelationships(entity, objectModel.getId(entity), relationshipLoader);
            if (existingGlobalCache) {
                globalCache.put(id, entity);
            }
            results.add(entity);
        }

        if (cache != null) {
            cache.insert(query, results, objectModel::getId);
        }

        return results;
    }

    public List<T> fetchFirstItem(@NotNull SQLDatabaseResult databaseResult) {
        ID id = resolverRegistry.resolve(idClass).resolve(databaseResult, repositoryModel.getPrimaryKey().columnName());
        if (repositoryModel.hasRelationships()) {
            T construct = objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, resolverRegistry, id));
            objectModel.populateRelationships(construct, id, relationshipLoader);
            return List.of(construct);
        }
        return List.of(objectModel.construct(GeneratedValueReaders.get(repositoryModel.tableName(), databaseResult, resolverRegistry, id)));
    }
}
