package io.github.flameyossnowy.universal.microservices.file;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transaction context for file-based repositories with batch operation support.
 * <p>
 * This implementation buffers file operations and applies them in batches for better performance.
 * Batch operations are automatically flushed when the batch size reaches a threshold
 * or when the transaction is committed.
 */
public class FileTransactionContext implements TransactionContext<FileContext> {
    private static final int DEFAULT_BATCH_SIZE = 1000;
    
    private final FileContext context;
    private final List<Runnable> pendingOperations = new ArrayList<>();
    private final AtomicInteger batchSize = new AtomicInteger(0);
    private final int maxBatchSize;
    private boolean committed = false;
    private boolean rolledBack = false;

    /**
     * Creates a new transaction context with default batch size.
     */
    public FileTransactionContext() {
        this(DEFAULT_BATCH_SIZE);
    }

    /**
     * Creates a new transaction context with custom batch size.
     *
     * @param maxBatchSize the maximum number of operations to buffer before flushing
     */
    public FileTransactionContext(int maxBatchSize) {
        this.context = new FileContext(null, true);
        this.maxBatchSize = maxBatchSize > 0 ? maxBatchSize : DEFAULT_BATCH_SIZE;
    }

    @Override
    public FileContext connection() {
        return context;
    }

    /**
     * Adds an operation to the current batch and flushes if batch size is reached.
     *
     * @param operation the operation to execute
     * @return true if the operation was added, false if the transaction is already committed/rolled back
     */
    public boolean addOperation(Runnable operation) {
        if (committed || rolledBack) {
            return false;
        }
        
        pendingOperations.add(operation);
        
        // Flush if batch size is reached
        if (batchSize.incrementAndGet() >= maxBatchSize) {
            flush();
        }
        
        return true;
    }
    
    /**
     * Flushes all pending operations to disk.
     */
    public void flush() {
        if (committed || rolledBack || pendingOperations.isEmpty()) {
            return;
        }
        
        try {
            // Execute all operations in the current batch
            for (Runnable op : pendingOperations) {
                op.run();
            }
            pendingOperations.clear();
            batchSize.set(0);
        } catch (Exception e) {
            // If any operation fails, rollback the transaction
            rollback();
            throw new RuntimeException("Failed to flush batch operations", e);
        }
    }

    @Override
    public TransactionResult<Boolean> commit() {
        if (rolledBack) {
            return TransactionResult.failure(new IllegalStateException("Transaction already rolled back"));
        }
        if (committed) {
            return TransactionResult.success(true);
        }
        
        try {
            // Flush any remaining operations
            flush();
            committed = true;
            return TransactionResult.success(true);
        } catch (Exception e) {
            rollback();
            return TransactionResult.failure(e);
        }
    }

    @Override
    public void rollback() {
        if (committed) {
            throw new IllegalStateException("Transaction already committed");
        }
        
        // Clear pending operations without executing them
        pendingOperations.clear();
        batchSize.set(0);
        rolledBack = true;
    }

    @Override
    public void close() {
        if (!committed && !rolledBack) {
            rollback();
        }
    }
    
    /**
     * @return true if there are pending operations that haven't been flushed
     */
    public boolean hasPendingOperations() {
        return !pendingOperations.isEmpty();
    }
    
    /**
     * @return the number of pending operations in the current batch
     */
    public int getPendingOperationCount() {
        return pendingOperations.size();
    }
}
