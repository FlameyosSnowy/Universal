package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlResultMapper;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.function.Function;

/**
 * SQL implementation of aggregation and complex query methods.
 * This is added to AbstractRelationalRepositoryAdapter.
 */
public class SqlAggregationImplementation<T, ID> {
    
    private final SQLConnectionProvider dataSource;
    private final AggregationQueryParser<T, ID> aggregationParser;
    private final SubQueryParser<T, ID> subQueryParser;
    private final RepositoryModel<T, ID> repositoryModel;
    private final TypeResolverRegistry resolverRegistry;
    private final SqlResultMapper<T, ID> resultMapper;
    private final CollectionHandler collectionHandler;
    private final boolean supportsArrays;

    public SqlAggregationImplementation(
        SQLConnectionProvider dataSource,
        RepositoryModel<T, ID> repositoryModel,
        TypeResolverRegistry resolverRegistry,
        QueryParseEngine.SQLType dialect, SqlResultMapper<T, ID> resultMapper, CollectionHandler collectionHandler, boolean supportsArrays) {
        this.dataSource = dataSource;
        this.repositoryModel = repositoryModel;
        this.resolverRegistry = resolverRegistry;
        this.resultMapper = resultMapper;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;
        this.aggregationParser = new AggregationQueryParser<>(repositoryModel, resolverRegistry, dialect);
        this.subQueryParser = new SubQueryParser<>(repositoryModel, resolverRegistry, dialect);
    }

    /**
     * Execute aggregation query and return raw results.
     */
    public List<Map<String, Object>> aggregate(@NotNull AggregationQuery query) {
        AggregationQueryParser.ParameterizedSql pq = aggregationParser.parseParameterized(query);
        String sql = pq.sql();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
        ) {

            if (!pq.params().isEmpty()) {
                SQLDatabaseParameters parameters = new SQLDatabaseParameters(
                    stmt,
                    resolverRegistry,
                    sql,
                    repositoryModel,
                    collectionHandler,
                    supportsArrays
                );
                bindPositional(parameters, pq.params());
            }

            try (ResultSet rs = stmt.executeQuery()) {
            
                return resultSetToMapList(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute aggregation query: " + sql, e);
        }
    }

    /**
     * Execute aggregation query and map each row to an entity using a caller-provided mapper.
     *
     * <p>This overload exists so repository adapters can map rows using generated ObjectModel code
     * (or any other strategy) instead of reflection.</p>
     */
    public List<T> aggregateEntities(@NotNull AggregationQuery query) {
        AggregationQueryParser.ParameterizedSql pq = aggregationParser.parseParameterized(query);
        String sql = pq.sql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            if (!pq.params().isEmpty()) {
                SQLDatabaseParameters parameters = new SQLDatabaseParameters(
                    stmt,
                    resolverRegistry,
                    sql,
                    repositoryModel,
                    collectionHandler,
                    supportsArrays
                );
                bindPositional(parameters, pq.params());
            }

            try (ResultSet rs = stmt.executeQuery()) {

                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(resultMapper.constructNewEntity(
                        new SQLDatabaseResult(
                            rs,
                            resolverRegistry,
                            collectionHandler,
                            supportsArrays,
                            repositoryModel
                        )
                    ));
                }
                return out;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute aggregation query: " + sql, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void bindPositional(SQLDatabaseParameters parameters, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            if (v == null) {
                parameters.setNull(String.valueOf(i + 1), Object.class);
                continue;
            }

            var resolver = (io.github.flameyossnowy.universal.api.resolver.TypeResolver<Object>) resolverRegistry.resolve(v.getClass());
            if (resolver != null) {
                resolver.insert(parameters, String.valueOf(i + 1), v);
            } else {
                parameters.setRaw(String.valueOf(i + 1), v, v.getClass());
            }
        }
    }

    /**
     * Execute window function query.
     */
    public List<Map<String, Object>> window(@NotNull WindowQuery query) {
        String sql = parseWindowQuery(query);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            return resultSetToMapList(rs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute window query: " + sql, e);
        }
    }

    /**
     * Execute window function query and map to result type.
     */
    public <R> List<R> window(@NotNull WindowQuery query, @NotNull Class<R> resultType) {
        if (resultType == repositoryModel.getEntityClass()) {
            @SuppressWarnings("unchecked")
            List<R> out = (List<R>) windowEntities(query);
            return out;
        }
        throw new UnsupportedOperationException(
            "Typed window projections are not supported. Use window(query) for Map results, or windowEntities(query) for entities."
        );
    }

    /**
     * Execute window query and map each row to an entity using generated ObjectModel code.
     */
    public @NotNull List<T> windowEntities(@NotNull WindowQuery query) {
        return windowEntities(query, rs ->
            resultMapper.constructNewEntity(
                new SQLDatabaseResult(
                    rs,
                    resolverRegistry,
                    collectionHandler,
                    supportsArrays,
                    repositoryModel
                )
            )
        );
    }

    /**
     * Execute window query and map each row to an entity using a caller-provided mapper.
     */
    public List<T> windowEntities(@NotNull WindowQuery query, @NotNull Function<ResultSet, T> rowMapper) {
        String sql = parseWindowQuery(query);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<T> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rowMapper.apply(rs));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute window query: " + sql, e);
        }
    }

    /**
     * Execute raw aggregation query.
     */
    public List<Map<String, Object>> executeAggregation(@NotNull Object rawQuery) {
        if (!(rawQuery instanceof String sql)) {
            throw new IllegalArgumentException("SQL aggregation requires String query");
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            return resultSetToMapList(rs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute raw aggregation: " + sql, e);
        }
    }

    /**
     * Get scalar value from aggregation.
     */
    public <R> R aggregateScalar(
            @NotNull AggregationQuery query,
            @NotNull String fieldName,
            @NotNull Class<R> type) {
        List<Map<String, Object>> results = aggregate(query);
        
        if (results.isEmpty()) {
            return null;
        }
        
        Object value = results.getFirst().get(fieldName);
        if (value == null) {
            return null;
        }
        
        return castValue(value, type);
    }

    /**
     * Parse window query to SQL.
     */
    private String parseWindowQuery(WindowQuery query) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // Build SELECT clause with window functions
        boolean seenSelect = false;
        for (FieldDefinition field : query.selectFields()) {
            if (field instanceof WindowFieldDefinition window) {
                if (seenSelect) {
                    sql.append(", ");
                } else {
                    seenSelect = true;
                }
                sql.append(buildWindowFunction(window));
            } else if (field instanceof SimpleFieldDefinition simple) {
                if (seenSelect) {
                    sql.append(", ");
                } else {
                    seenSelect = true;
                }
                sql.append(simple.getFieldName());
                if (simple.alias() != null && !simple.alias().equals(simple.getFieldName())) {
                    sql.append(" AS ").append(simple.alias());
                }
            }
        }
        
        // FROM clause
        if (query.fromTable() != null) {
            sql.append(" FROM ").append(getTableName(query.fromTable()));
        } else {
            sql.append(" FROM ").append(repositoryModel.tableName());
        }
        
        // WHERE clause
        if (!query.whereFilters().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(query.whereFilters()));
        }
        
        // ORDER BY clause
        if (!query.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            boolean seen = false;
            StringBuilder acc = null;
            for (SortOption s : query.orderBy()) {
                String string = s.field() + " " + s.order().name();
                if (!seen) {
                    seen = true;
                    acc = new StringBuilder(string);
                } else {
                    acc.append(", ").append(string);
                }
            }
            sql.append(seen ? acc.toString() : "");
        }
        
        // LIMIT clause
        if (query.limit() > 0) {
            sql.append(" LIMIT ").append(query.limit());
        }
        
        return sql.toString();
    }

    /**
     * Build window function SQL.
     */
    private String buildWindowFunction(WindowFieldDefinition window) {
        StringBuilder sql = new StringBuilder();
        
        // Function name
        sql.append(window.functionType().name());
        
        // Function arguments
        if (isAggregateWindowFunction(window.functionType())) {
            sql.append("(").append(window.field()).append(")");
        } else {
            sql.append("()");
        }
        
        // OVER clause
        sql.append(" OVER (");

        boolean wroteClause = false;

        // PARTITION BY
        if (!window.partitionBy().isEmpty()) {
            sql.append("PARTITION BY ");
            boolean seen = false;
            for (String f : window.partitionBy()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(f);
            }
            wroteClause = true;
        }

        // ORDER BY
        if (!window.orderBy().isEmpty()) {
            if (wroteClause) {
                sql.append(' ');
            }
            sql.append("ORDER BY ");
            boolean seen = false;
            for (SortOption s : window.orderBy()) {
                if (seen) {
                    sql.append(", ");
                } else {
                    seen = true;
                }
                sql.append(s.field()).append(' ').append(s.order().name());
            }
        }
        
        // Frame clause
        if (window.frameStart() != null && window.frameEnd() != null) {
            sql.append(" ROWS BETWEEN ")
               .append(window.frameStart())
               .append(" AND ")
               .append(window.frameEnd());
        }
        
        sql.append(")");
        
        // Alias
        if (window.alias() != null) {
            sql.append(" AS ").append(window.alias());
        }
        
        return sql.toString();
    }

    private boolean isAggregateWindowFunction(WindowFunctionType type) {
        return type == WindowFunctionType.COUNT ||
               type == WindowFunctionType.SUM ||
               type == WindowFunctionType.AVG ||
               type == WindowFunctionType.MIN ||
               type == WindowFunctionType.MAX;
    }

    /**
     * Build WHERE clause from filters.
     */
    private String buildWhereClause(List<FilterOption> filters) {
        boolean seen = false;
        StringBuilder acc = null;
        for (FilterOption filter : filters) {
            String s = buildFilterClause(filter);
            if (!seen) {
                seen = true;
                acc = new StringBuilder(s);
            } else {
                acc.append(" AND ").append(s);
            }
        }
        return seen ? acc.toString() : "1=1";
    }

    private String buildFilterClause(FilterOption filter) {
        if (filter instanceof SelectOption(String option, String operator, Object value)) {
            return option + " " + operator + " " + formatValue(value);
        }
        throw new IllegalArgumentException("Unsupported filter type: " + filter.getClass());
    }

    /**
     * Convert ResultSet to List of Maps.
     */
    private List<Map<String, Object>> resultSetToMapList(ResultSet rs) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>(Math.max(16, (int) (columnCount / 0.75f) + 1));
            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            results.add(row);
        }
        
        return results;
    }

    /**
     * Cast value to target type with proper conversions.
     */
    @SuppressWarnings("unchecked")
    private <R> R castValue(Object value, Class<R> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isInstance(value)) {
            return (R) value;
        }
        
        // Handle numeric conversions
        if (value instanceof Number num) {
            if (targetType == Long.class || targetType == long.class) {
                return (R) Long.valueOf(num.longValue());
            }
            if (targetType == Integer.class || targetType == int.class) {
                return (R) Integer.valueOf(num.intValue());
            }
            if (targetType == Double.class || targetType == double.class) {
                return (R) Double.valueOf(num.doubleValue());
            }
            if (targetType == Float.class || targetType == float.class) {
                return (R) Float.valueOf(num.floatValue());
            }
            if (targetType == Short.class || targetType == short.class) {
                return (R) Short.valueOf(num.shortValue());
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return (R) Byte.valueOf(num.byteValue());
            }
        }
        
        // Handle string conversions
        if (targetType == String.class) {
            return (R) value.toString();
        }
        
        // Default: try direct cast
        return targetType.cast(value);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return "'" + value + "'";
        }
    }

    private String getTableName(Class<?> entityClass) {
        // Try to get table name from metadata, fallback to class name
        try {
            var model = io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(entityClass);
            if (model != null) {
                return model.tableName();
            }
        } catch (Exception ignored) {
        }
        return entityClass.getSimpleName().toLowerCase();
    }
}