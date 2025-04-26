package io.github.flameyossnowy.universal.api.listener;

import java.util.List;

public interface AuditLogger<T> {
    void onInsert(T entity);

    void onInsert(List<T> entities);

    void onUpdate(T newEntity, T oldEntity);

    void onDelete(T entity);
}
