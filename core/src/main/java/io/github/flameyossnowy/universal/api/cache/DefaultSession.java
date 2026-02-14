package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.utils.Logging;

import java.util.*;

public class DefaultSession<ID, T, C> implements DatabaseSession<ID, T, C> {
    private final SessionCache<ID, T> cache;
    private final RepositoryAdapter<T, ID, C> repository;
    private final TransactionContext<C> transactionContext;
    private final RepositoryModel<T, ID> information;
    private final long id;
    private final EnumSet<SessionOption> options;

    private final List<Runnable> pendingOperations = new ArrayList<>(5);
    private final List<Runnable> rollbackCallbacks = new ArrayList<>(5);
    private final List<TransactionResult<?>> results = new ArrayList<>(5);

    public DefaultSession(RepositoryAdapter<T, ID, C> repository, SessionCache<ID, T> cache, long id, EnumSet<SessionOption> options) {
        this.repository = repository;
        this.transactionContext = repository.beginTransaction();
        this.cache = cache;
        this.information = repository.getRepositoryModel();
        this.id = id;
        this.options = options != null ? options : EnumSet.noneOf(SessionOption.class);
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
        cache.clear();
    }

    @Override
    public C connection() {
        return transactionContext.connection();
    }

    @Override
    public T findById(ID key) {
        return options.contains(SessionOption.NO_CACHE)
                ? repository.findById(key)
                : cache.computeIfAbsent(key, repository::findById);
    }

    @Override
    public TransactionResult<Boolean> commit() {
        if (!options.contains(SessionOption.BUFFERED_WRITE)) {
            for (Runnable action : pendingOperations) {
                action.run();
            }
        }

        for (TransactionResult<?> result : results) {
            if (!result.isError()) continue;
            rollback();
            return TransactionResult.failure(result.getError().orElse(new IllegalStateException("Operation returned false")));
        }

        try {
            transactionContext.commit();
        } catch (Exception e) {
            rollback();
            return TransactionResult.failure(e);
        }

        pendingOperations.clear();
        rollbackCallbacks.clear();
        results.clear();
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
        ID entityId = information.getPrimaryKeyValue(entity);

        Runnable operation = () -> {
            TransactionResult<Boolean> result = repository.insert(entity, transactionContext);
            results.add(result);
            if (!result.getResult().orElse(Boolean.FALSE)) return;
            cache.put(entityId, entity);
        };

        if (options.contains(SessionOption.LOG_OPERATIONS)) {
            log("INSERT " + entity);
        }

        if (options.contains(SessionOption.BUFFERED_WRITE)) {
            pendingOperations.add(operation);
        } else {
            operation.run();
        }

        rollbackCallbacks.add(() -> cache.remove(entityId));
        return true;
    }

    @Override
    public boolean delete(T entity) {
        ID entityId = information.getPrimaryKeyValue(entity);
        T previous = findById(entityId);

        Runnable operation = () -> {
            TransactionResult<Boolean> result = repository.delete(entity, transactionContext);
            results.add(result);
            if (result.getResult().orElse(Boolean.FALSE)) return;
            cache.remove(entityId);
        };

        if (options.contains(SessionOption.LOG_OPERATIONS)) {
            log("DELETE " + entity);
        }

        if (options.contains(SessionOption.BUFFERED_WRITE)) {
            pendingOperations.add(operation);
        } else {
            operation.run();
        }

        rollbackCallbacks.add(() -> {
            if (previous != null) cache.put(entityId, previous);
        });
        return true;
    }

    @Override
    public boolean update(T entity) {
        ID entityId = information.getPrimaryKeyValue(entity);
        T previous = findById(entityId);

        Runnable operation = () -> {
            TransactionResult<Boolean> result = repository.updateAll(entity, transactionContext);
            results.add(result);
            if (result.getResult().orElse(Boolean.FALSE)) return;
            cache.put(entityId, entity);
        };

        if (options.contains(SessionOption.LOG_OPERATIONS)) {
            log("UPDATE " + entity);
        }

        if (options.contains(SessionOption.BUFFERED_WRITE)) {
            pendingOperations.add(operation);
        } else {
            operation.run();
        }

        rollbackCallbacks.add(() -> {
            if (previous != null) cache.put(entityId, previous);
        });
        return true;
    }
    
    private void log(String message) {
        Logging.info(() -> "[DefaultSession " + id + "] " + message);
    }
}
