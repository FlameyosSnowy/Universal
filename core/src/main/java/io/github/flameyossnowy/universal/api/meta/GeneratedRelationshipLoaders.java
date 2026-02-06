package io.github.flameyossnowy.universal.api.meta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.github.flameyossnowy.universal.api.factory.RelationshipLoader;
import io.github.flameyossnowy.universal.api.handler.RelationshipHandler;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;

public final class GeneratedRelationshipLoaders {

    /**
     * Factory function that creates a RelationshipLoader given handlers.
     */
    @FunctionalInterface
    public interface RelationshipLoaderFactory<E, ID> {
        RelationshipLoader<E, ID> create(
            RelationshipHandler<E, ID> handler,
            CollectionHandler collectionHandler,
            RepositoryModel<E, ID> model
        );
    }

    // Store factories instead of instances
    private static final Map<String, RelationshipLoaderFactory<?, ?>> FACTORIES =
        new ConcurrentHashMap<>();

    // Cache created loaders per handler pair
    private static final Map<String, Map<HandlerKey, RelationshipLoader<?, ?>>> LOADER_CACHE =
        new ConcurrentHashMap<>();

    private GeneratedRelationshipLoaders() {}

    /**
     * Register a factory for creating relationship loaders.
     */
    public static <E, L> void add(String repoName, RelationshipLoaderFactory<E, L> factory) {
        FACTORIES.put(repoName, factory);
        LOADER_CACHE.put(repoName, new ConcurrentHashMap<>());
    }

    /**
     * Get or create a RelationshipLoader for the given repository and handlers.
     * Results are cached based on the handler instances.
     */
    @SuppressWarnings("unchecked")
    public static <E, ID> RelationshipLoader<E, ID> get(
        String repoName,
        RelationshipHandler<E, ID> handler,
        CollectionHandler collectionHandler,
        RepositoryModel<E, ID> model
    ) {
        RelationshipLoaderFactory factory = FACTORIES.get(repoName);
        if (factory == null) {
            throw new IllegalArgumentException("No relationship loader factory registered for: " + repoName);
        }

        Map<HandlerKey, RelationshipLoader<?, ?>> cache = LOADER_CACHE.get(repoName);
        HandlerKey key = new HandlerKey(handler, collectionHandler);

        return (RelationshipLoader<E, ID>) cache.computeIfAbsent(
            key,
            k -> factory.create(handler, collectionHandler, model)
        );
    }

    /**
     * Clear all cached loaders for a specific repository.
     */
    public static void clearCache(String repoName) {
        Map<HandlerKey, RelationshipLoader<?, ?>> cache = LOADER_CACHE.get(repoName);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Clear all cached loaders across all repositories.
     */
    public static void clearAllCaches() {
        LOADER_CACHE.values().forEach(Map::clear);
    }

    /**
     * Key for caching loaders based on handler instances.
     */
    private static final class HandlerKey {
        private final RelationshipHandler<?, ?> relationshipHandler;
        private final CollectionHandler collectionHandler;
        private final int hashCode;

        HandlerKey(RelationshipHandler<?, ?> relationshipHandler, CollectionHandler collectionHandler) {
            this.relationshipHandler = relationshipHandler;
            this.collectionHandler = collectionHandler;
            // Pre-compute hash since handlers are immutable references
            this.hashCode = 31 * System.identityHashCode(relationshipHandler)
                + System.identityHashCode(collectionHandler);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HandlerKey that)) return false;
            // Use identity comparison since we want same instances
            return relationshipHandler == that.relationshipHandler
                && collectionHandler == that.collectionHandler;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}