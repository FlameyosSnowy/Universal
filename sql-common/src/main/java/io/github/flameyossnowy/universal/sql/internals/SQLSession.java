package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.cache.Session;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;

import java.sql.Connection;
import java.util.*;

public class SQLSession<ID, T> implements Session<ID, T, Connection> {
    private final RelationalRepositoryAdapter<T, ID> repository;
    private final SessionCache<ID, T> cache;
    private final TransactionContext<Connection> transactionContext;
    private final RepositoryInformation information;
    private final long id;

    private final List<Runnable> pendingOperations = new ArrayList<>(5);
    private final List<TransactionResult<?>> results = new ArrayList<>(5);

    private final EnumSet<SessionOption> options;

    public SQLSession(RelationalRepositoryAdapter<T, ID> repository, SessionCache<ID, T> cache, long id) {
        this(repository, cache, id, EnumSet.noneOf(SessionOption.class));
    }

    public SQLSession(RelationalRepositoryAdapter<T, ID> repository, SessionCache<ID, T> cache, long id, EnumSet<SessionOption> options) {
        this.repository = repository;
        this.transactionContext = repository.beginTransaction();
        this.cache = cache;
        this.information = repository.getInformation();
        this.id = id;
        this.options = options;
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
    public Connection connection() {
        return transactionContext.connection();
    }

    @Override
    public T findById(ID key) {
        if (options.contains(SessionOption.NO_CACHE)) {
            return repository.findById(key);
        }
        return cache.computeIfAbsent(key, repository::findById);
    }

    @Override
    public TransactionResult<Boolean> commit() {
        for (Runnable action : pendingOperations) {
            action.run();
        }

        for (TransactionResult<?> result : results) {
            if (!result.isError()) continue;
            rollback();
            return TransactionResult.failure(result.getError().orElseGet(() -> new IllegalStateException("Operation returned false")));
        }

        pendingOperations.clear();
        results.clear();
        return transactionContext.commit();
    }

    @Override
    public void rollback() {
        pendingOperations.clear();

        try {
            transactionContext.rollback();
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage(), e);
        }
    }

    @Override
    public boolean insert(T entity) {
        ID entityId = information.getPrimaryKey().getValue(entity);
        Runnable operation = () -> {
            log("Inserting entity: " + entity);
            TransactionResult<Boolean> result = repository.insert(entity, transactionContext);
            results.add(result);
            if (!result.getResult().orElse(false)) return;
            cache.put(entityId, entity);
        };

        if (options.contains(SessionOption.BUFFERED_WRITE)) {
            pendingOperations.add(operation);
        } else {
            operation.run();
        }

        return true;
    }

    @Override
    public boolean delete(T entity) {
        ID entityId = information.getPrimaryKey().getValue(entity);
        Runnable operation = () -> {
            log("Deleting entity: " + entity);
            TransactionResult<Boolean> result = repository.delete(entity, transactionContext);
            results.add(result);
            if (!result.getResult().orElse(false)) return;
            cache.remove(entityId);
        };

        if (options.contains(SessionOption.BUFFERED_WRITE)) {
            pendingOperations.add(operation);
        } else {
            operation.run();
        }

        return true;
    }

    @Override
    public boolean update(T entity) {
        ID entityId = information.getPrimaryKey().getValue(entity);
        Runnable operation = () -> {
            log("Updating entity: " + entity);
            TransactionResult<Boolean> result = repository.updateAll(entity, transactionContext);
            results.add(result);
            if (!result.getResult().orElse(false)) return;
            cache.put(entityId, entity);
        };

        if (options.contains(SessionOption.BUFFERED_WRITE)) {
            pendingOperations.add(operation);
        } else {
            operation.run();
        }

        return true;
    }

    private void log(String message) {
        if (options.contains(SessionOption.LOG_OPERATIONS)) {
            Logging.info("[SQLSession " + id + "] " + message);
        }
    }
}