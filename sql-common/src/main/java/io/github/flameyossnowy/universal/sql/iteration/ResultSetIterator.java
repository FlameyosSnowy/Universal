package io.github.flameyossnowy.universal.sql.iteration;

import io.github.flameyossnowy.universal.api.CloseableIterator;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import io.github.flameyossnowy.universal.sql.result.SQLDatabaseResult;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Iterator that buffers and iterates over a ResultSet with configurable fetch size.
 * 
 * @param <T> the type of elements returned by this iterator
 */
public class ResultSetIterator<T> implements CloseableIterator<T> {
    private final ResultSet resultSet;
    private final BiFunction<ResultSet, SQLDatabaseResult, T> mapper;
    private final SQLDatabaseResult sqlDatabaseParameters;
    private final TypeResolverRegistry resolverRegistry;
    private final CollectionHandler collectionHandler;
    private Boolean hasNext;
    private final boolean supportArrays;
    private boolean closed = false;

    /**
     * Creates an iterator with default fetch size (as determined by ResultSet).
     */
    public ResultSetIterator(
        ResultSet resultSet, BiFunction<ResultSet, SQLDatabaseResult, T> mapper, TypeResolverRegistry resolverRegistry,
        CollectionHandler collectionHandler, boolean supportArrays, RepositoryModel<T, ?> information
    ) {
        this(resultSet, mapper, null, resolverRegistry, collectionHandler, supportArrays, information);
    }

    /**
     * Creates an iterator with specified fetch size.
     * 
     * @param fetchSize the number of rows to fetch at a time, or null for default
     */
    public ResultSetIterator(
        ResultSet resultSet, BiFunction<ResultSet, SQLDatabaseResult, T> mapper, Integer fetchSize, TypeResolverRegistry resolverRegistry,
        CollectionHandler collectionHandler, boolean supportsArrays, RepositoryModel<T, ?> information
    ) {
        this.resultSet = resultSet;
        this.mapper = mapper;
        this.resolverRegistry = resolverRegistry;
        this.collectionHandler = collectionHandler;
        this.supportArrays = supportsArrays;
        this.sqlDatabaseParameters = new SQLDatabaseResult(resultSet, resolverRegistry, collectionHandler, supportsArrays, information);

        if (fetchSize != null) {
            try {
                resultSet.setFetchSize(fetchSize);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set fetch size", e);
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        
        if (hasNext == null) {
            try {
                hasNext = resultSet.next();
                if (!hasNext) {
                    close();
                }
            } catch (SQLException e) {
                close();
                throw new RuntimeException("Error checking for next result", e);
            }
        }
        return hasNext;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more elements in ResultSet");
        }
        
        try {
            T result = mapper.apply(resultSet, sqlDatabaseParameters);
            hasNext = null; // Reset for next hasNext() call
            return result;
        } catch (Exception e) {
            close();
            throw new RuntimeException("Error mapping ResultSet row", e);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                resultSet.close();
            } catch (SQLException e) {
                // Log but don't throw in close()
                System.err.println("Error closing ResultSet: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a Stream from a ResultSet with default fetch size.
     */
    public static <T> Stream<T> stream(
        ResultSet resultSet, BiFunction<ResultSet, SQLDatabaseResult, T> mapper, TypeResolverRegistry resolverRegistry,
        CollectionHandler collectionHandler, boolean supportArrays, RepositoryModel<T, ?> information
    ) {
        return stream(resultSet, mapper, null, resolverRegistry, collectionHandler, supportArrays, information);
    }

    /**
     * Creates a Stream from a ResultSet with specified fetch size.
     * The stream will automatically close the ResultSet when terminal operation completes.
     * 
     * @param fetchSize the number of rows to fetch at a time, or null for default
     */
    public static <T> Stream<T> stream(
        ResultSet resultSet, BiFunction<ResultSet, SQLDatabaseResult, T> mapper, Integer fetchSize,
        TypeResolverRegistry resolverRegistry, CollectionHandler collectionHandler, boolean supportArrays, RepositoryModel<T, ?> information
    ) {
        ResultSetIterator<T> iterator = new ResultSetIterator<>(resultSet, mapper, fetchSize, resolverRegistry, collectionHandler, supportArrays, information);
        
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, 0),
            false
        ).onClose(() -> {
            try {
                iterator.close();
            } catch (Exception e) {
                throw new RuntimeException("Error closing iterator", e);
            }
        });
    }
}