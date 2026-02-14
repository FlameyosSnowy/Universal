package io.github.flameyossnowy.universal.microservices.file;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance file-based database session with batch operation support.
 * <p>
 * This implementation provides:
 * - Batch processing of operations
 * - In-memory caching of entities
 * - Optimized file I/O with configurable batch sizes
 * - Thread-safe operation
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class FileSession<T, ID> implements DatabaseSession<ID, T, FileContext> {
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(0);
    
    private final long sessionId;
    private final FileRepositoryAdapter<T, ID> adapter;
    private final EnumSet<SessionOption> options;
    private final SessionCache<ID, T> cache;
    private final List<Operation> operations;
    private final Map<ID, T> insertBatch = new ConcurrentHashMap<>(5);
    private final Map<ID, T> updateBatch = new ConcurrentHashMap<>(5);
    private final List<ID> deleteBatch = new ArrayList<>(5);
    private final int batchSize;
    private boolean closed = false;
    private boolean autoFlush;

    /**
     * Creates a new file session with default batch size.
     *
     * @param adapter the repository adapter to use
     * @param options session options
     */
    public FileSession(FileRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options) {
        this(adapter, options, DEFAULT_BATCH_SIZE, true);
    }

    /**
     * Creates a new file session with custom batch size.
     *
     * @param adapter the repository adapter to use
     * @param options session options
     * @param batchSize the batch size for bulk operations
     * @param autoFlush whether to automatically flush when batch size is reached
     */
    public FileSession(FileRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options, 
                      int batchSize, boolean autoFlush) {
        this.sessionId = SESSION_ID_GENERATOR.incrementAndGet();
        this.adapter = adapter;
        this.options = options != null ? options : EnumSet.noneOf(SessionOption.class);
        this.cache = new FileSessionCache<>();
        this.operations = new ArrayList<>();
        this.batchSize = Math.max(1, batchSize);
        this.autoFlush = autoFlush;
    }

    @Override
    public SessionCache<ID, T> getCache() {
        checkClosed();
        return cache;
    }

    @Override
    public long getId() {
        return sessionId;
    }

    @Override
    public void rollback() {
        checkClosed();
        operations.clear();
        insertBatch.clear();
        updateBatch.clear();
        deleteBatch.clear();
        cache.clear();
    }

    @Override
    public boolean insert(T entity) {
        checkClosed();
        ID id = adapter.extractId(entity);

        operations.add(new Operation(OperationType.INSERT, entity));
        insertBatch.put(id, entity);
        cache.put(id, entity);

        if (autoFlush && insertBatch.size() >= batchSize) {
            flushInserts();
        }
        return true;
    }
    
    /**
     * Inserts multiple entities in a batch.
     *
     * @param entities the entities to insert
     * @return true if all inserts were successful
     */
    public boolean insertAll(Iterable<T> entities) {
        checkClosed();
        boolean result = true;
        for (T entity : entities) {
            result &= insert(entity);
        }
        return result;
    }

    @Override
    public boolean delete(T entity) {
        checkClosed();
        ID id = adapter.extractId(entity);

        operations.add(new Operation(OperationType.DELETE, entity));
        deleteBatch.add(id);
        cache.remove(id);

        // Remove from insert/update batches if present
        insertBatch.remove(id);
        updateBatch.remove(id);

        if (autoFlush && deleteBatch.size() >= batchSize) {
            flushDeletes();
        }
        return true;
    }
    
    /**
     * Deletes multiple entities in a batch.
     *
     * @param entities the entities to delete
     * @return true if all deletes were successful
     */
    public boolean deleteAll(Iterable<T> entities) {
        checkClosed();
        boolean result = true;
        for (T entity : entities) {
            result &= delete(entity);
        }
        return result;
    }

    @Override
    public boolean update(T entity) {
        checkClosed();
        ID id = adapter.extractId(entity);

        // If this entity is in the insert batch, just update it there
        if (insertBatch.containsKey(id)) {
            insertBatch.put(id, entity);
        } else {
            operations.add(new Operation(OperationType.UPDATE, entity));
            updateBatch.put(id, entity);

            if (autoFlush && updateBatch.size() >= batchSize) {
                flushUpdates();
            }
        }

        cache.put(id, entity);
        return true;
    }
    
    /**
     * Updates multiple entities in a batch.
     *
     * @param entities the entities to update
     * @return true if all updates were successful
     */
    public boolean updateAll(Iterable<T> entities) {
        checkClosed();
        boolean result = true;
        for (T entity : entities) {
            result &= update(entity);
        }
        return result;
    }

    @Override
    public T findById(ID key) {
        checkClosed();
        
        // Check cache first
        T cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // Check insert/update batches before going to disk
        {
            T entity = insertBatch.get(key);
            if (entity == null) {
                entity = updateBatch.get(key);
            }
            if (entity != null) {
                cache.put(key, entity);
                return entity;
            }
        }
        
        // Not in any batch, check the actual storage
        T entity = adapter.findById(key);
        if (entity != null) {
            cache.put(key, entity);
        }
        return entity;
    }
    
    /**
     * Finds multiple entities by their IDs in a batch.
     *
     * @param ids the IDs to find
     * @return a map of ID to entity for all found entities
     */
    public Map<ID, T> findAllById(List<ID> ids) {
        checkClosed();
        Map<ID, T> result = new ConcurrentHashMap<>(ids.size());
        
        // First check cache and batches
        List<ID> missingIds = new ArrayList<>(ids.size());
        for (ID id : ids) {
            T entity = findById(id);
            if (entity != null) {
                result.put(id, entity);
            } else {
                missingIds.add(id);
            }
        }
        
        // If we have missing IDs, try to load them in a batch
        if (!missingIds.isEmpty()) {
            Map<ID, T> batchResult = adapter.findAllById(missingIds);
            result.putAll(batchResult);
            
            // Cache the results
            batchResult.forEach(cache::put);
        }
        
        return result;
    }

    @Override
    public TransactionResult<Boolean> commit() {
        checkClosed();
        
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            // Process all batches
            flushInserts();
            flushUpdates();
            flushDeletes();
            
            // Process any remaining operations
            if (!operations.isEmpty()) {
                for (Operation op : operations) {
                    switch (op.type) {
                        case INSERT -> {
                            TransactionResult<Boolean> insert = adapter.insert(op.entity, tx);
                            if (insert.isError()) return insert;
                        }
                        case UPDATE -> {
                            TransactionResult<Boolean> update = adapter.updateAll(op.entity, tx);
                            if (update.isError()) return update;
                        }
                        case DELETE -> {
                            TransactionResult<Boolean> delete = adapter.delete(op.entity, tx);
                            if (delete.isError()) return delete;
                        }
                    }
                }
                operations.clear();
            }
            
            return tx.commit();
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }
    
    /**
     * Flushes all pending inserts to disk.
     */
    public void flushInserts() {
        if (insertBatch.isEmpty()) {
            return;
        }
        
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            adapter.insertAll(insertBatch.values(), tx).ifError(e -> {
                throw new RuntimeException(e);
            });
            tx.commit();
            insertBatch.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush inserts", e);
        }
    }
    
    /**
     * Flushes all pending updates to disk.
     */
    public void flushUpdates() {
        if (updateBatch.isEmpty()) {
            return;
        }
        
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            for (T entity : updateBatch.values()) {
                adapter.updateAll(entity, tx).ifError(e -> {
                    throw new RuntimeException(e);
                });
            }
            tx.commit();
            updateBatch.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush updates", e);
        }
    }
    
    /**
     * Flushes all pending deletes to disk.
     */
    public void flushDeletes() {
        if (deleteBatch.isEmpty()) {
            return;
        }
        
        try (FileTransactionContext tx = new FileTransactionContext(batchSize)) {
            for (ID id : deleteBatch) {
                adapter.deleteById(id, tx).ifError(e -> {
                    throw new RuntimeException(e);
                });
            }
            tx.commit();
            deleteBatch.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush deletes", e);
        }
    }
    
    /**
     * Flushes all pending operations to disk.
     */
    public void flush() {
        checkClosed();
        flushInserts();
        flushUpdates();
        flushDeletes();
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                if (autoFlush) {
                    flush();
                }
            } finally {
                rollback();
                closed = true;
            }
        }
    }

    @Override
    public FileContext connection() {
        checkClosed();
        return new FileContext(adapter.getBasePath(), true);
    }
    
    /**
     * Sets whether to automatically flush when batch size is reached.
     *
     * @param autoFlush true to enable auto-flush, false to disable
     */
    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }
    
    /**
     * @return the current batch size
     */
    public int getBatchSize() {
        return batchSize;
    }
    
    /**
     * @return the number of pending operations
     */
    public int getPendingOperationCount() {
        return operations.size() + insertBatch.size() + updateBatch.size() + deleteBatch.size();
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Session is closed");
        }
    }

    // Helper classes

    private enum OperationType {
        INSERT, UPDATE, DELETE
    }

    private class Operation {
        private final OperationType type;
        private final T entity;

        public Operation(OperationType type, T entity) {
            this.type = type;
            this.entity = entity;
        }
    }
}
