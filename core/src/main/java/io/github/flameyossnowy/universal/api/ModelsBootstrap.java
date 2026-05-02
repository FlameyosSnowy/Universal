package io.github.flameyossnowy.universal.api;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelsBootstrap {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        // Invoke register() on all discovered factories to trigger metadata registration
        ServiceLoader.load(
            GeneratedRepositoryFactory.class,
            ModelsBootstrap.class.getClassLoader()
        ).forEach(GeneratedRepositoryFactory::register);
    }
}
