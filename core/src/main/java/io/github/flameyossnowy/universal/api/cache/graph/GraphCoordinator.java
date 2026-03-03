package io.github.flameyossnowy.universal.api.cache.graph;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;

/**
 * Coordinates cascading operations across an entity relationship graph.
 *
 * <p>Implementations are <strong>not thread-safe</strong> and are typically short-lived
 * per cascade operation. They maintain internal state (e.g., visited sets) to prevent
 * infinite recursion during graph traversal.
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>Do not share instances across threads.</li>
 *   <li>Do not reuse the same instance for multiple cascade operations unless documented.</li>
 * </ul>
 *
 * <h3>Performance and Safety</h3>
 * <ul>
 *   <li>Cascading can be expensive for large or deeply nested graphs.</li>
 *   <li>Prefer explicit transaction boundaries when cascading many entities.</li>
 *   <li>Be aware of cyclic graphs; implementations must use visited sets based on entity IDs.</li>
 * </ul>
 *
 * @param <ID> Primary key type
 * @param <T> Entity type
 */
public interface GraphCoordinator<ID, T> {

    /**
     * Cascades an insert operation through the entity graph.
     *
     * <p>Inserts the root entity and traverses relationships, inserting related
     * entities as configured by {@code @Relationship(cascadesInsert = true)}.
     *
     * <p><strong>Concurrency:</strong> The provided session should not be used
     * concurrently during this operation.
     *
     * <p><strong>I/O:</strong> This may perform multiple database writes.
     *
     * @param root the root entity to insert
     * @param session the session to use for writes; must not be null
     */
    void cascadeInsert(T root, DatabaseSession<ID, T, ?> session);

    /**
     * Cascades an update operation through the entity graph.
     *
     * <p>Updates the root entity and traverses relationships, updating related
     * entities as configured by {@code @Relationship(cascadesUpdate = true)}.
     *
     * <p><strong>Concurrency:</strong> The provided session should not be used
     * concurrently during this operation.
     *
     * <p><strong>I/O:</strong> This may perform multiple database writes.
     *
     * @param root the root entity to update
     * @param session the session to use for writes; must not be null
     */
    void cascadeUpdate(T root, DatabaseSession<ID, T, ?> session);

    /**
     * Cascades a delete operation through the entity graph.
     *
     * <p>Deletes the root entity and traverses relationships, deleting related
     * entities as configured by {@code @Relationship(cascadesDelete = true)}.
     *
     * <p><strong>Concurrency:</strong> The provided session should not be used
     * concurrently during this operation.
     *
     * <p><strong>I/O:</strong> This may perform multiple database writes.
     *
     * @param root the root entity to delete
     * @param session the session to use for writes; must not be null
     */
    void cascadeDelete(T root, DatabaseSession<ID, T, ?> session);
}
