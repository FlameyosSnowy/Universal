package io.github.flameyossnowy.universal.api.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LazyBatchContext<ID> {
    private static final ThreadLocal<LazyBatchContext<?>> CTX =
        ThreadLocal.withInitial(LazyBatchContext::new);

    @SuppressWarnings("unchecked")
    public static <ID> LazyBatchContext<ID> current() {
        return (LazyBatchContext<ID>) CTX.get();
    }

    /**
     * Clears the current thread's batch context and removes the ThreadLocal.
     * Call this after batch operations complete to prevent memory leaks in pooled threads.
     */
    public static void clear() {
        CTX.remove();
    }

    private final Map<LazyBatchKey, List<ID>> ids =
        new HashMap<>();

    public void register(LazyBatchKey key, ID id) {
        ids.computeIfAbsent(key, ignored -> new ArrayList<>(8))
           .add(id);
    }

    public List<ID> drain(LazyBatchKey key) {
        return ids.remove(key);
    }

    public boolean hasPending() {
        return !ids.isEmpty();
    }

    /**
     * Clears all pending IDs. Call this after processing all batches.
     */
    public void clearAll() {
        ids.clear();
    }
}
