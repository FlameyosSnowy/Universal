package io.github.flameyossnowy.universal.api;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelsBootstrap {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        // not much else to do
        //noinspection ResultOfMethodCallIgnored
        ServiceLoader.load(
            GeneratedRepositoryFactory.class,
            ModelsBootstrap.class.getClassLoader()
        ).forEach(GeneratedRepositoryFactory::getClass);
    }
}
