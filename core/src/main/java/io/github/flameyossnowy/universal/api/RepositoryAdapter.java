package io.github.flameyossnowy.universal.api;

import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
public interface RepositoryAdapter<T, ID, C> extends AutoCloseable {
    List<T> find(SelectQuery query);

    List<T> find();

    default T findById(ID key) {
        return first(Query.select().where("id", "=", key).build());
    }

    T first(SelectQuery query);

    default T first() {
        return first(null);
    }

    default void createRepository() {
        this.createRepository(false);
    }

    void createRepository(boolean ifNotExists);

    TransactionContext<C> beginTransaction() throws Exception;

    void insert(T value, TransactionContext<C> transactionContext);

    void insertAll(List<T> value, TransactionContext<C> transactionContext);

    void updateAll(UpdateQuery query, TransactionContext<C> transactionContext);

    void delete(DeleteQuery query, TransactionContext<C> transactionContext);

    void createIndex(IndexOptions index);

    default void createIndexes(IndexOptions... indexes) {
        for (IndexOptions elements : indexes) {
            this.createIndex(elements);
        }
    }

    default void insert(T value) {
        try (var transactionContext = beginTransaction()) {
            insert(value, transactionContext);
            transactionContext.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default void updateAll(UpdateQuery query) {
        try (var transactionContext = beginTransaction()) {
            updateAll(query, transactionContext);
            transactionContext.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default void delete(DeleteQuery query) {
        try (var transactionContext = beginTransaction()) {
            delete(query, transactionContext);
            transactionContext.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default void insertAll(Collection<T> query) {
        try (var transactionContext = beginTransaction()) {
            insertAll(query, transactionContext);
            transactionContext.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default CompletableFuture<List<T>> findAsync(SelectQuery query) {
        return CompletableFuture.supplyAsync(() -> find(query));
    }

    default CompletableFuture<List<T>> findAsync() {
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

    void clear();
}
