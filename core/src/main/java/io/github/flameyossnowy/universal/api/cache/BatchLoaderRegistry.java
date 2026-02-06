package io.github.flameyossnowy.universal.api.cache;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BatchLoaderRegistry {

    private static final AtomicBoolean PENDING = new AtomicBoolean(false);

    /** mark that some batch-loads are pending */
    public static void markPending() {
        PENDING.set(true);
    }

    /** check if any batch-loads are pending */
    public static boolean hasPending() {
        return PENDING.get();
    }

    /** clear pending flag after flush */
    public static void clearPending() {
        PENDING.set(false);
    }
}
