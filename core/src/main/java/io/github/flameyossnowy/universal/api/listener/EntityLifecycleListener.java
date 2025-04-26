package io.github.flameyossnowy.universal.api.listener;

import org.jetbrains.annotations.Nullable;

public interface EntityLifecycleListener<T> {
    default void onPrePersist(@Nullable T entity) {}

    default void onPostPersist(@Nullable T entity) {}

    default void onPreUpdate(@Nullable T entity) {}

    default void onPostUpdate(@Nullable T entity) {}

    default void onPreDelete(@Nullable T entity) {}

    default void onPostDelete(@Nullable T entity) {}
}
