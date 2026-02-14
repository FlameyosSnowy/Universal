package io.github.flameyossnowy.universal.microservices.network;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.cache.SessionCache;
import io.github.flameyossnowy.universal.api.cache.SessionOption;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance network-based database session with batch operation support.
 * <p>
 * This implementation provides:
 * - Batch processing of operations
 * - Parallel request execution
 * - Request deduplication
 * - Automatic retry for failed requests
 * - Connection pooling
 *
 * @param <T> The entity type
 * @param <ID> The ID type
 */
public class NetworkSession<T, ID> implements DatabaseSession<ID, T, HttpClient> {
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 16;
    private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(0);
    
    private final long sessionId;
    private final NetworkRepositoryAdapter<T, ID> adapter;
    private final EnumSet<SessionOption> options;
    private final SessionCache<ID, T> cache;
    private final ExecutorService executorService;
    private final Map<ID, CompletableFuture<T>> pendingFutures = new ConcurrentHashMap<>(3);
    private final Map<ID, T> insertBatch = new ConcurrentHashMap<>(3);
    private final Map<ID, T> updateBatch = new ConcurrentHashMap<>(3);
    private final Set<ID> deleteBatch = ConcurrentHashMap.newKeySet();
    private final int batchSize;
    private final int maxRetries;
    private final int maxConcurrentRequests;
    private boolean closed = false;
    private boolean autoFlush = true;

    /**
     * Creates a new network session with default settings.
     *
     * @param adapter the repository adapter to use
     * @param options session options
     */
    public NetworkSession(NetworkRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options) {
        this(adapter, options, DEFAULT_BATCH_SIZE, DEFAULT_MAX_RETRIES, DEFAULT_MAX_CONCURRENT_REQUESTS);
    }

    /**
     * Creates a new network session with custom settings.
     *
     * @param adapter the repository adapter to use
     * @param options session options
     * @param batchSize the batch size for bulk operations
     * @param maxRetries maximum number of retries for failed requests
     * @param maxConcurrentRequests maximum number of concurrent HTTP requests
     */
    public NetworkSession(NetworkRepositoryAdapter<T, ID> adapter, EnumSet<SessionOption> options,
                         int batchSize, int maxRetries, int maxConcurrentRequests) {
        this.sessionId = SESSION_ID_GENERATOR.incrementAndGet();
        this.adapter = adapter;
        this.options = options != null ? options : EnumSet.noneOf(SessionOption.class);
        this.cache = new NetworkSessionCache<>();
        this.batchSize = Math.max(1, batchSize);
        this.maxRetries = Math.max(0, maxRetries);
        this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        this.executorService = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors(), this.maxConcurrentRequests)
        );
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
        insertBatch.clear();
        updateBatch.clear();
        deleteBatch.clear();
        pendingFutures.clear();
        cache.clear();
    }

    @Override
    public boolean insert(T entity) {
        checkClosed();
        ID id = adapter.extractId(entity);

        // Remove from delete batch if present
        deleteBatch.remove(id);
        // Add to insert batch
        insertBatch.put(id, entity);
        // Update cache
        cache.put(id, entity);

        // Auto-flush if batch size is reached
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

        // Remove from insert/update batches if present
        insertBatch.remove(id);
        updateBatch.remove(id);
        // Add to delete batch
        deleteBatch.add(id);
        // Update cache
        cache.remove(id);

        // Auto-flush if batch size is reached
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
            // Remove from delete batch if present
            deleteBatch.remove(id);
            // Add to update batch
            updateBatch.put(id, entity);

            // Auto-flush if batch size is reached
            if (autoFlush && updateBatch.size() >= batchSize) {
                flushUpdates();
            }
        }

        // Update cache
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

        T entity = insertBatch.get(key);
        if (entity == null) {
            entity = updateBatch.get(key);
        }
        if (entity != null) {
            cache.put(key, entity);
            return entity;
        }

        // Check if there's already a pending request for this ID
        CompletableFuture<T> future = pendingFutures.get(key);
        if (future != null) {
            try {
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch entity: " + key, e);
            }
        }
        
        // Not in cache or batches, fetch from network
        return fetchFromNetwork(key);
    }
    
    private T fetchFromNetwork(ID key) {
        // Create a new future for this request
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture<T> existing = pendingFutures.putIfAbsent(key, future);
        
        if (existing != null) {
            // Another thread is already fetching this entity, wait for it
            try {
                return existing.get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch entity: " + key, e);
            }
        }
        
        try {
            // Execute the request with retry
            T result = withRetry(() -> adapter.findById(key), maxRetries);
            
            // Complete the future and update cache
            future.complete(result);
            if (result != null) {
                cache.put(key, result);
            }
            
            return result;
        } catch (Exception e) {
            future.completeExceptionally(e);
            throw new RuntimeException("Failed to fetch entity: " + key, e);
        } finally {
            // Clean up the future
            pendingFutures.remove(key, future);
        }
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
        List<ID> missingIds = new ArrayList<>(ids.size());
        
        // First check cache and batches
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
            Map<ID, T> batchResult;
            try {
                batchResult = withRetry(
                    () -> adapter.findAllById(missingIds),
                    maxRetries
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Cache the results
            batchResult.forEach((id, entity) -> {
                cache.put(id, entity);
                result.put(id, entity);
            });
        }
        
        return result;
    }

    @Override
    public TransactionResult<Boolean> commit() {
        checkClosed();
        
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), 
            batchSize, 
            maxConcurrentRequests
        )) {
            // Process all batches
            flushInserts(tx);
            flushUpdates(tx);
            flushDeletes(tx);
            
            return tx.commit();
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }
    
    /**
     * Flushes all pending inserts to the server.
     */
    public void flushInserts() {
        if (insertBatch.isEmpty()) {
            return;
        }
        
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), 
            batchSize, 
            maxConcurrentRequests
        )) {
            flushInserts(tx);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush inserts", e);
        }
    }
    
    private void flushInserts(NetworkTransactionContext tx) {
        if (insertBatch.isEmpty()) {
            return;
        }
        
        try {
            // Convert batch to list and clear it
            List<T> batch = new ArrayList<>(insertBatch.values());
            insertBatch.clear();
            
            // Execute batch insert
            withRetry(() -> {
                adapter.insertAll(batch, tx).ifError(e -> {
                    throw new RuntimeException(e);
                });
                return (Void) null;
            }, maxRetries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush inserts", e);
        }
    }

    /**
     * Flushes all pending updates to the server.
     */
    public void flushUpdates() {
        if (updateBatch.isEmpty()) {
            return;
        }
        
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), 
            batchSize, 
            maxConcurrentRequests
        )) {
            flushUpdates(tx);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush updates", e);
        }
    }
    
    private void flushUpdates(NetworkTransactionContext tx) {
        if (updateBatch.isEmpty()) {
            return;
        }
        
        try {
            // Convert batch to list and clear it
            List<T> batch = new ArrayList<>(updateBatch.values());
            updateBatch.clear();

            // Execute batch update
            for (T entity : batch) {
                withRetry(() -> {
                    adapter.updateAll(entity, tx).ifError(e -> {
                        throw new RuntimeException(e);
                    });
                    return (Void) null;
                }, maxRetries);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush updates", e);
        }
    }
    
    /**
     * Flushes all pending deletes to the server.
     */
    public void flushDeletes() {
        if (deleteBatch.isEmpty()) {
            return;
        }
        
        try (NetworkTransactionContext tx = new NetworkTransactionContext(
            adapter.getHttpClient(), 
            batchSize, 
            maxConcurrentRequests
        )) {
            flushDeletes(tx);
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush deletes", e);
        }
    }
    
    private void flushDeletes(NetworkTransactionContext tx) {
        if (deleteBatch.isEmpty()) {
            return;
        }
        
        try {
            // Convert batch to list and clear it
            List<ID> batch = new ArrayList<>(deleteBatch);
            deleteBatch.clear();
            
            // Execute batch delete
            for (ID id : batch) {
                withRetry(() -> {
                    adapter.deleteById(id, tx).ifError(e -> {
                        throw new RuntimeException(e);
                    });
                    return (Void) null;
                }, maxRetries);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to flush deletes", e);
        }
    }
    
    /**
     * Flushes all pending operations to the server.
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
                executorService.shutdown();
            }
        }
    }

    @Override
    public HttpClient connection() {
        checkClosed();
        return adapter.getHttpClient();
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
        return insertBatch.size() + updateBatch.size() + deleteBatch.size() + pendingFutures.size();
    }
    
    /**
     * Executes a task with retry logic.
     *
     * @param task the task to execute
     * @param maxRetries maximum number of retries
     * @param <R> the result type
     * @return the result of the task
     * @throws Exception if all retries fail
     */
    private static <R> R withRetry(ThrowingSupplier<R> task, int maxRetries) throws Exception {
        int attempts = 0;
        Exception lastError = null;
        
        while (attempts <= maxRetries) {
            try {
                return task.get();
            } catch (Exception e) {
                lastError = e;
                if (attempts == maxRetries) {
                    break;
                }
                
                // Exponential backoff
                try {
                    Thread.sleep((long) Math.pow(2, attempts) * 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Operation interrupted", ie);
                }
                
                attempts++;
            }
        }
        
        throw lastError != null ? lastError : new RuntimeException("Operation failed after " + maxRetries + " retries");
    }
    
    @FunctionalInterface
    private interface ThrowingSupplier<R> {
        R get() throws Exception;
    }
    
    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Session is closed");
        }
    }
}
