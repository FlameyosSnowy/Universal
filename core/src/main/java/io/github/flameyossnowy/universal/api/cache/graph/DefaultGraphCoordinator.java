package io.github.flameyossnowy.universal.api.cache.graph;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;
import io.github.flameyossnowy.universal.api.meta.RelationshipModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings({ "unchecked", "unused" })
public final class DefaultGraphCoordinator<ID, T> implements GraphCoordinator<ID, T> {

    private final RepositoryModel<T, ID> model;
    private final Set<ID> visited = new HashSet<>();

    DefaultGraphCoordinator(RepositoryModel<T, ID> model) {
        this.model = model;
    }

    @Override
    public void cascadeInsert(T root, DatabaseSession<ID, T, ?> session) {
        walkInsert(root, session);
    }

    @Override
    public void cascadeUpdate(T root, DatabaseSession<ID, T, ?> session) {
        walkUpdate(root, session);
    }

    @Override
    public void cascadeDelete(T root, DatabaseSession<ID, T, ?> session) {
        walkDelete(root, session);
    }

    /* ---------------- internal walkers ---------------- */

    private void walkInsert(T entity, DatabaseSession<ID, T, ?> session) {
        ID id = model.getPrimaryKeyValue(entity);
        if (id != null && !visited.add(id)) return;

        for (RelationshipModel<T, ID> rel : model.getRelationships()) {
            if (!rel.isOwning() || !rel.cascadesInsert()) continue;

            Object value = rel.get(entity);
            if (value == null) continue;

            if (rel.isCollection()) {
                for (Object child : (Iterable<?>) value) {
                    walkInsert((T) child, session);
                }
            } else {
                walkInsert((T) value, session);
            }
        }

        session.insert(entity);
    }

    private void walkUpdate(T entity, DatabaseSession<ID, T, ?> session) {
        ID id = model.getPrimaryKeyValue(entity);
        if (id != null && !visited.add(id)) return;

        for (RelationshipModel<T, ID> rel : model.getRelationships()) {
            if (!rel.isOwning() || !rel.cascadesUpdate()) continue;

            Object value = rel.get(entity);
            if (value == null) continue;

            if (rel.isCollection()) {
                for (Object child : (Iterable<?>) value) {
                    walkUpdate((T) child, session);
                }
            } else {
                walkUpdate((T) value, session);
            }
        }

        session.update(entity);
    }

    private void walkDelete(T entity, DatabaseSession<ID, T, ?> session) {
        ID id = model.getPrimaryKeyValue(entity);
        if (id != null && !visited.add(id)) return;

        for (RelationshipModel<T, ID> rel : model.getRelationships()) {
            if (!rel.isOwning() || !rel.cascadesDelete()) continue;

            Object value = rel.get(entity);
            if (value == null) continue;

            if (rel.isCollection()) {
                for (Object child : (Iterable<?>) value) {
                    walkDelete((T) child, session);
                }
            } else {
                walkDelete((T) value, session);
            }
        }

        session.delete(entity);
    }
}
