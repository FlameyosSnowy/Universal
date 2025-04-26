package io.github.flameyossnowy.universal.mongodb;

import com.mongodb.client.ClientSession;
import io.github.flameyossnowy.universal.api.cache.Session;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

import java.util.*;

public class MongoSession<ID, T> implements Session<ID, T, ClientSession> {
    private final SessionCache<ID, T> cache;
    private final MongoRepositoryAdapter<T, ID> repository;
    private final TransactionContext<ClientSession> transactionContext;
    private final RepositoryInformation information;
    private final long id;

    private final List<Runnable> pendingOperations = new ArrayList<>(5);
    private final List<Runnable> rollbackCallbacks = new ArrayList<>(5);

    public MongoSession(MongoRepositoryAdapter<T, ID> repository, SessionCache<ID, T> cache, long id) {
        this.repository = repository;
        this.transactionContext = repository.beginTransaction();
        this.cache = cache;
        this.information = repository.getInformation();
        this.id = id;
    }

    @Override
    public SessionCache<ID, T> getCache() {
        return cache;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void close() {
        transactionContext.close();
    }

    @Override
    public ClientSession connection() {
        return transactionContext.connection();
    }

    @Override
    public T findById(ID key) {
        return cache.computeIfAbsent(key, repository::findById);
    }

    @Override
    public TransactionResult<Void> commit() {
        for (Runnable action : pendingOperations) {
            action.run();
        }
        try {
            transactionContext.commit();
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
        pendingOperations.clear();
        rollbackCallbacks.clear();
        return TransactionResult.success(null);
    }

    @Override
    public void rollback() {
        rollbackCallbacks.forEach(Runnable::run);
        pendingOperations.clear();
        rollbackCallbacks.clear();
        try {
            transactionContext.rollback();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean insert(T entity) {
        ID entityId = information.getPrimaryKey().getValue(entity);
        pendingOperations.add(() -> {
            if (repository.insert(entity, transactionContext)) {
                cache.put(entityId, entity);
            }
        });
        rollbackCallbacks.add(() -> cache.remove(entityId));
        return true;
    }

    @Override
    public boolean delete(T entity) {
        ID entityId = information.getPrimaryKey().getValue(entity);
        T previous = findById(entityId);
        pendingOperations.add(() -> {
            if (repository.delete(entity, transactionContext)) {
                cache.remove(entityId);
            }
        });
        rollbackCallbacks.add(() -> {
            if (previous != null) cache.put(entityId, previous);
        });
        return true;
    }

    @Override
    public boolean update(T entity) {
        ID entityId = information.getPrimaryKey().getValue(entity);
        T previous = findById(entityId);
        pendingOperations.add(() -> {
            if (repository.updateAll(entity, transactionContext)) {
                cache.put(entityId, entity);
            }
        });
        rollbackCallbacks.add(() -> {
            if (previous != null) cache.put(entityId, previous);
        });
        return true;
    }
}