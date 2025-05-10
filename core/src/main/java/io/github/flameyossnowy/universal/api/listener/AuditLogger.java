package io.github.flameyossnowy.universal.api.listener;

import java.util.List;

public interface AuditLogger<T> {
    /**
     * Called when an entity is inserted into the repository.
     * This method is used to perform any audit or logging actions
     * associated with the insertion of a single entity.
     *
     * @param entity The entity that was inserted.
     */
    void onInsert(T entity);

    /**
     * Called when multiple entities are inserted into the repository.
     * This method is used to perform any audit or logging actions
     * associated with the insertion of a list of entities.
     *
     * @param entities The list of entities that were inserted.
     */
    void onInsert(List<T> entities);

    /**
     * Called when an entity is updated in the repository.
     * This method is used to perform any audit or logging actions
     * associated with the update of a single entity.
     *
     * @param newEntity The entity that was updated.
     * @param oldEntity The old entity that was updated.
     */
    void onUpdate(T newEntity, T oldEntity);

    /**
     * Called when an entity is deleted from the repository.
     * This method is used to perform any audit or logging actions
     * associated with the deletion of a single entity.
     *
     * @param entity The entity that was deleted.
     */
    void onDelete(T entity);
}
