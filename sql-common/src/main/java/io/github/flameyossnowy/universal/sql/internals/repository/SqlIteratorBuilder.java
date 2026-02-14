package io.github.flameyossnowy.universal.sql.internals.repository;

import io.github.flameyossnowy.universal.api.CloseableIterator;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.cache.DefaultResultCache;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.exceptions.handler.ExceptionHandler;
import io.github.flameyossnowy.universal.api.factory.ObjectModel;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.listener.AuditLogger;
import io.github.flameyossnowy.universal.api.listener.EntityLifecycleListener;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.DelegatingResultSet;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.iteration.ResultSetIterator;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class SqlIteratorBuilder<T, ID> {
    private final SQLConnectionProvider dataSource;
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;
    private final ObjectModel<T, ID> objectModel;
    private final Class<ID> idClass;

    private final SqlParameterBinder<T, ID> parameterBinder;
    private final QueryParseEngine.SQLType sqlType;

    private final SqlResultMapper<T, ID> resultMapper;
    private final QueryParseEngine<T, ID> engine;
    private final RelationshipLoader<T, ID> relationshipLoader;

    public SqlIteratorBuilder(
        SQLConnectionProvider dataSource,
        RepositoryModel<T, ID> repositoryModel,
        TypeResolverRegistry resolverRegistry,
        CollectionHandler collectionHandler,
        boolean supportsArrays,
        ObjectModel<T, ID> objectModel,
        Class<ID> idClass,
        SqlParameterBinder<T, ID> parameterBinder,
        QueryParseEngine.SQLType sqlType,
        SqlResultMapper<T, ID> resultMapper,
        QueryParseEngine<T, ID> engine,
        RelationshipLoader<T, ID> relationshipLoader
    ) {
        this.dataSource = dataSource;
        this.repositoryModel = repositoryModel;
        this.resolverRegistry = resolverRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;
        this.objectModel = objectModel;
        this.idClass = idClass;
        this.parameterBinder = parameterBinder;
        this.sqlType = sqlType;
        this.resultMapper = resultMapper;
        this.engine = engine;
        this.relationshipLoader = relationshipLoader;
    }

    public @NotNull CloseableIterator<T> findIterator(SelectQuery q) {
        try {
            String sql = engine.parseSelect(q, false);

            BiFunction<ResultSet, SQLDatabaseResult, T> mapper = (r, result) -> {
                try {
                    return resultMapper.constructNewEntity(result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };

            return executeForIteration(
                sql,
                q == null ? List.of() : q.filters(),
                rs -> new ResultSetIterator<>(
                    rs, mapper, repositoryModel.getFetchPageSize(), resolverRegistry,
                    collectionHandler, supportsArrays, repositoryModel
                )
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create iterator", e);
        }
    }

    public @NotNull Stream<T> findStream(SelectQuery q) {
        try {
            String sql = engine.parseSelect(q, false);

            return executeForIteration(
                sql,
                q == null ? List.of() : q.filters(),
                rs -> ResultSetIterator.stream(
                    rs,
                    (r, result) -> {
                        try {
                            return resultMapper.constructNewEntity(result);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    },
                    repositoryModel.getFetchPageSize() < 0 ? null : repositoryModel.getFetchPageSize(),
                    resolverRegistry,
                    collectionHandler,
                    supportsArrays,
                    repositoryModel
                )
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create stream", e);
        }
    }

    private <R> R executeForIteration(
        String sql,
        List<FilterOption> filters,
        Function<ResultSet, R> resultSetConsumer
    ) throws Exception {
        Connection connection = dataSource.getConnection();
        PreparedStatement statement = dataSource.prepareStatement(sql, connection);

        int fetchSize = repositoryModel.getFetchPageSize() > 0
            ? repositoryModel.getFetchPageSize()
            : 100;

        statement.setFetchSize(fetchSize);

        SQLDatabaseParameters parameters =
            new SQLDatabaseParameters(statement, resolverRegistry, sql, repositoryModel, collectionHandler, supportsArrays);

        this.parameterBinder.addFilterToPreparedStatement(filters, parameters, resolverRegistry, repositoryModel, sqlType);

        ResultSet resultSet = statement.executeQuery();

        // IMPORTANT: cleanup must cascade from the ResultSet
        return resultSetConsumer.apply(
            new DelegatingResultSet(resultSet, statement, connection)
        );
    }
}
