package io.github.flameyossnowy.universal.api;

import io.github.flameyossnowy.universal.api.cache.FetchedDataResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
public interface RepositoryAdapter<T, ID, C> extends AutoCloseable {
    FetchedDataResult<T, ID> find(SelectQuery query);

    FetchedDataResult<T, ID> find();

    T findById(ID key);

    T first(SelectQuery query);

    default T first() {
        return first(null);
    }

    default void createRepository() {
        this.createRepository(false);
    }

    void createRepository(boolean ifNotExists);

    TransactionContext<C> beginTransaction() throws Exception;

    boolean insert(T value, TransactionContext<C> transactionContext);

    void insertAll(List<T> value, TransactionContext<C> transactionContext);

    boolean updateAll(UpdateQuery query, TransactionContext<C> transactionContext);

    boolean delete(DeleteQuery query, TransactionContext<C> transactionContext);

    boolean delete(T value);

    void createIndex(IndexOptions index);

    void clear();

    default void createIndexes(IndexOptions... indexes) {
        for (IndexOptions elements : indexes) {
            this.createIndex(elements);
        }
    }

    <A> A createDynamicProxy(Class<A> adapter);

    boolean insert(T value);

    boolean updateAll(UpdateQuery query);

    boolean delete(DeleteQuery query);

    void insertAll(List<T> query);

    Class<ID> getIdType();

    default CompletableFuture<FetchedDataResult<T, ID>> findAsync(SelectQuery query) {
        return CompletableFuture.supplyAsync(() -> find(query));
    }

    default CompletableFuture<FetchedDataResult<T, ID>> findAsync() {
        return CompletableFuture.supplyAsync(this::find);
    }

    default CompletableFuture<Void> insertAsync(T value) {
        return CompletableFuture.runAsync(() -> insert(value));
    }

    default CompletableFuture<Void> updateAllAsync(UpdateQuery query) {
        return CompletableFuture.runAsync(() -> updateAll(query));
    }

    default CompletableFuture<Void> deleteAsync(DeleteQuery query) {
        return CompletableFuture.runAsync(() -> delete(query));
    }

    default CompletableFuture<Void> createRepositoryAsync() {
        return CompletableFuture.runAsync(this::createRepository);
    }

    default CompletableFuture<Void> clearAsync() {
        return CompletableFuture.runAsync(this::clear);
    }
}
