package io.github.flameyossnowy.universal.microservices.file.executor;

import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Executes write mutations: insert, update-by-query, and delete-by-query.
 * Delegates raw I/O to {@link FileEntityStore}, filter evaluation to
 * {@link FileFilterEngine}, and index bookkeeping to {@link FileIndexManager}.
 */
public class FileMutationExecutor<T, ID> {

    private final RepositoryModel<T, ID> repositoryModel;
    private final FileEntityStore<T, ID> store;
    private final FileFilterEngine<T, ID> filterEngine;
    private final FileIndexManager<T, ID> indexManager;

    public FileMutationExecutor(
            @NotNull RepositoryModel<T, ID> repositoryModel,
            @NotNull FileEntityStore<T, ID> store,
            @NotNull FileFilterEngine<T, ID> filterEngine,
            @NotNull FileIndexManager<T, ID> indexManager
    ) {
        this.repositoryModel = repositoryModel;
        this.store           = store;
        this.filterEngine    = filterEngine;
        this.indexManager    = indexManager;
    }

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    public TransactionResult<Boolean> insert(T entity) {
        try {
            ID id = extractId(entity);
            store.write(entity, id);
            indexManager.onInsertOrUpdate(entity, id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    public TransactionResult<Boolean> insertAll(Collection<T> entities) {
        try {
            for (T entity : entities) {
                store.write(entity, extractId(entity));
            }
            indexManager.onInsertOrUpdateBatch(entities);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /** Writes a single entity back to disk (full replacement). */
    public TransactionResult<Boolean> updateEntity(T entity) {
        try {
            ID id = extractId(entity);
            store.write(entity, id);
            indexManager.onInsertOrUpdate(entity, id);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    /** Applies field-level updates to all entities that match the query's filters. */
    public TransactionResult<Boolean> updateByQuery(@NotNull UpdateQuery query) {
        try {
            List<T> all = store.readAll();
            List<T> updated = new ArrayList<>(all.size());

            for (T entity : all) {
                if (!filterEngine.matchesAll(entity, query.filters())) continue;

                applyUpdates(entity, query);
                store.write(entity, extractId(entity));
                updated.add(entity);
            }

            indexManager.onInsertOrUpdateBatch(updated);
            return TransactionResult.success(!updated.isEmpty());
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public TransactionResult<Boolean> deleteEntity(T entity) {
        try {
            ID id = extractId(entity);
            store.delete(id);
            indexManager.onDelete(id, entity);
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    public TransactionResult<Boolean> deleteById(ID id, T entity) {
        try {
            store.delete(id);
            if (entity != null) {
                indexManager.onDelete(id, entity);
            }
            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    public TransactionResult<Boolean> deleteByQuery(@NotNull DeleteQuery query) {
        try {
            List<T> all = store.readAll();
            List<T> deleted = new ArrayList<>(all.size());

            for (T entity : all) {
                if (!filterEngine.matchesAll(entity, query.filters())) continue;

                store.delete(extractId(entity));
                deleted.add(entity);
            }

            indexManager.onDeleteBatch(deleted);
            return TransactionResult.success(!deleted.isEmpty());
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void applyUpdates(T entity, UpdateQuery query) {
        Map<String, Object> updates = query.updates();
        updates.forEach((fieldName, newValue) -> {
            var field = repositoryModel.fieldByName(fieldName);
            if (field == null) throw new IllegalArgumentException("Unknown field: " + fieldName);
            try {
                field.setValue(entity, newValue);
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply update on field: " + fieldName, e);
            }
        });
    }

    private ID extractId(T entity) {
        return repositoryModel.getPrimaryKeyValue(entity);
    }
}