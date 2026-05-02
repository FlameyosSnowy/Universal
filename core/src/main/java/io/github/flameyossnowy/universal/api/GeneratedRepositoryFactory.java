package io.github.flameyossnowy.universal.api;

/**
 * Marker interface implemented by all generated repository factory classes.
 * Discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface GeneratedRepositoryFactory {
    /**
     * Called by {@link ModelsBootstrap} to register this factory's metadata
     * with the appropriate registries. Implementations should perform any
     * necessary registration (e.g., adding to GeneratedMetadata, etc.).
     */
    default void register() {
        // Default implementation does nothing.
        // Generated implementations override this to perform registration.
    }
}