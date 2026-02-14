package io.github.flameyossnowy.universal.api.cache.graph;

import io.github.flameyossnowy.universal.api.cache.DatabaseSession;

public interface GraphCoordinator<ID, T> {

    void cascadeInsert(T root, DatabaseSession<ID, T, ?> session);

    void cascadeUpdate(T root, DatabaseSession<ID, T, ?> session);

    void cascadeDelete(T root, DatabaseSession<ID, T, ?> session);
}
