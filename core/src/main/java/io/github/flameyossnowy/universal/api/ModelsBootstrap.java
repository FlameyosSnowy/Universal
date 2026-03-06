package io.github.flameyossnowy.universal.api;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelsBootstrap {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ServiceLoader.load(GeneratedRepositoryFactory.class, cl)
            .forEach(provider -> {
                // Nothing needed
            });

    }
}
