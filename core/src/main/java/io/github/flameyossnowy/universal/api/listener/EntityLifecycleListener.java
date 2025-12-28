package io.github.flameyossnowy.universal.api.listener;

import org.jetbrains.annotations.Nullable;

/**
 * Entity lifecycle listener to listen to entity lifecycle events.
 * @param <T>
 */
@SuppressWarnings("unused")
public interface EntityLifecycleListener<T> {
    /**
     * Called before the entity is persisted into the underlying storage.
     * @param entity the entity to be persisted, or null if the entity is null
     */
    default void onPreInsert(@Nullable T entity) {}

    /**
     * Called after the entity is persisted into the underlying storage.
     * @param entity the entity that was persisted, or null if the entity was null
     */
    default void onPostInsert(@Nullable T entity) {}

    /**
     * Called before the entity is updated in the underlying storage.
     * @param entity the entity to be updated, or null if the entity is null
     */
    default void onPreUpdate(@Nullable T entity) {}

    /**
     * Called after the entity is updated in the underlying storage.
     * @param entity the entity that was updated, or null if the entity was null
     */
    default void onPostUpdate(@Nullable T entity) {}

    /**
     * Called before the entity is deleted from the underlying storage.
     * @param entity the entity to be deleted, or null if the entity is null
     */
    default void onPreDelete(@Nullable T entity) {}

    /**
     * Called after the entity is deleted from the underlying storage.
     * @param entity the entity that was deleted, or null if the entity was null
     */
    default void onPostDelete(@Nullable T entity) {}
}
