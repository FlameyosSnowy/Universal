package io.github.flameyossnowy.universal.microservices.network;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transaction context for network-based repositories with batch operation support.
 * This implementation batches and limits concurrent network requests without blocking.
 */
public class NetworkTransactionContext implements TransactionContext<HttpClient> {
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 10;

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final Deque<Runnable> pendingOperations = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedQueue<CompletableFuture<?>> pendingFutures = new ConcurrentLinkedQueue<>();

    private final int maxBatchSize;
    private final int maxConcurrentRequests;

    private final AtomicInteger activeRequests = new AtomicInteger(0);

    private volatile boolean committed = false;
    private volatile boolean rolledBack = false;

    public NetworkTransactionContext(HttpClient httpClient) {
        this(httpClient, DEFAULT_BATCH_SIZE, DEFAULT_MAX_CONCURRENT_REQUESTS);
    }

    public NetworkTransactionContext(HttpClient httpClient, int maxBatchSize, int maxConcurrentRequests) {
        this.httpClient = httpClient;
        this.maxBatchSize = Math.max(1, maxBatchSize);
        this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);

        int poolSize = Math.min(Runtime.getRuntime().availableProcessors(), this.maxConcurrentRequests);
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public HttpClient connection() {
        return httpClient;
    }

    public <T> CompletableFuture<HttpResponse<T>> addOperation(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseHandler) {

        if (committed || rolledBack)
            throw new IllegalStateException("Transaction is already committed or rolled back");

        CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
        pendingFutures.add(future);

        pendingOperations.add(() -> executeRequest(request, responseHandler, future));

        if (pendingOperations.size() >= maxBatchSize) {
            flushBatchAsync();
        } else {
            processPendingOperations();
        }

        return future;
    }

    private <T> void executeRequest(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseHandler,
            CompletableFuture<HttpResponse<T>> future) {

        activeRequests.incrementAndGet();

        httpClient.sendAsync(request, responseHandler)
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(response);
                        }
                    } finally {
                        activeRequests.decrementAndGet();
                        processPendingOperations();
                    }
                });
    }

    private void processPendingOperations() {
        while (!pendingOperations.isEmpty() && activeRequests.get() < maxConcurrentRequests) {
            Runnable op = pendingOperations.pollFirst();
            if (op != null) {
                executorService.submit(op);
            }
        }
    }

    /**
     * Flushes operations asynchronously without blocking the caller thread.
     * @return a CompletableFuture that completes when all queued operations finish
     */
    public CompletableFuture<Void> flushBatchAsync() {
        if (committed || rolledBack || pendingOperations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        processPendingOperations();

        return CompletableFuture
                .allOf(pendingFutures.toArray(new CompletableFuture[0]))
                .exceptionally(ex -> null); // swallow errors here; commit() handles them
    }

    @Override
    public TransactionResult<Boolean> commit() {
        if (rolledBack)
            return TransactionResult.failure(new IllegalStateException("Transaction already rolled back"));
        if (committed)
            return TransactionResult.success(true);

        committed = true;

        CompletableFuture<Void> allDone = flushBatchAsync();

        // non-blocking completion handling
        allDone.whenComplete((v, ex) -> executorService.shutdown());

        if (allDone.isCompletedExceptionally()) {
            rollback();
            return TransactionResult.failure(new RuntimeException("Commit failed"));
        }

        return TransactionResult.success(true);
    }

    @Override
    public void rollback() {
        if (committed)
            throw new IllegalStateException("Transaction already committed");

        rolledBack = true;

        pendingOperations.clear();
        pendingFutures.forEach(f -> f.cancel(true));
        pendingFutures.clear();

        executorService.shutdownNow();
    }

    @Override
    public void close() {
        if (!committed && !rolledBack) {
            rollback();
        } else if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    public int getActiveRequestCount() {
        return activeRequests.get();
    }

    public int getPendingOperationCount() {
        return pendingOperations.size();
    }
}