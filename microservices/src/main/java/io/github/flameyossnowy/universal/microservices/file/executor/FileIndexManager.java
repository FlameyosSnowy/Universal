package io.github.flameyossnowy.universal.microservices.file.executor;

import io.github.flameyossnowy.universal.api.IndexOptions;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.microservices.file.indexes.SecondaryIndex;
import me.flame.uniform.json.JsonAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory secondary indexes: creation, per-entity updates, batch
 * updates/removals, and persistence. Knows nothing about queries or I/O beyond
 * what it needs to write index files.
 */
public class FileIndexManager<T, ID> {

    private final RepositoryModel<T, ID> repositoryModel;
    private final JsonAdapter objectMapper;
    private final Path indexRoot;

    private final Map<String, SecondaryIndex<ID>> indexes = new ConcurrentHashMap<>(16);

    public FileIndexManager(
            @NotNull RepositoryModel<T, ID> repositoryModel,
            @NotNull JsonAdapter objectMapper,
            @NotNull Path indexRoot
    ) {
        this.repositoryModel = repositoryModel;
        this.objectMapper    = objectMapper;
        this.indexRoot        = indexRoot;
    }

    // -------------------------------------------------------------------------
    // Index lifecycle
    // -------------------------------------------------------------------------

    public TransactionResult<Boolean> createIndex(@NotNull IndexOptions options, @NotNull List<T> allEntities) {
        String field = options.indexName();
        if (indexes.containsKey(field)) return TransactionResult.success(false);

        try {
            SecondaryIndex<ID> idx = new SecondaryIndex<>(field, options.type() == IndexType.UNIQUE);

            for (T entity : allEntities) {
                Object value = repositoryModel.fieldByName(field).getValue(entity);
                ID id        = repositoryModel.getPrimaryKeyValue(entity);
                idx.map().computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet()).add(id);
            }

            indexes.put(field, idx);
            persistIndex(idx);

            return TransactionResult.success(true);
        } catch (Exception e) {
            return TransactionResult.failure(e);
        }
    }

    public void clearAll() {
        indexes.clear();
    }

    public boolean isEmpty() {
        return indexes.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Per-entity updates
    // -------------------------------------------------------------------------

    public void onInsertOrUpdate(T entity, ID id) {
        if (indexes.isEmpty()) return;
        for (SecondaryIndex<ID> index : indexes.values()) {
            try {
                Object value = repositoryModel.fieldByName(index.field()).getValue(entity);
                index.map().computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet()).add(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void onDelete(ID id, T entity) {
        if (indexes.isEmpty()) return;
        indexes.values().forEach(index -> {
            try {
                Object value = repositoryModel.fieldByName(index.field()).getValue(entity);
                Set<ID> ids  = index.map().get(value);
                if (ids != null) {
                    ids.remove(id);
                    if (ids.isEmpty()) index.map().remove(value);
                }
            } catch (Exception ignored) {}
        });
    }

    // -------------------------------------------------------------------------
    // Batch updates
    // -------------------------------------------------------------------------

    public void onInsertOrUpdateBatch(Collection<T> entities) {
        if (entities.isEmpty() || indexes.isEmpty()) return;

        for (SecondaryIndex<ID> index : indexes.values()) {
            FieldModel<T> field = repositoryModel.fieldByName(index.field());
            Map<Object, Set<ID>> additions = collectValueToIds(entities, field);

            for (Map.Entry<Object, Set<ID>> entry : additions.entrySet()) {
                index.map()
                    .computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet())
                    .addAll(entry.getValue());
            }
        }
    }

    public void onDeleteBatch(Collection<T> entities) {
        if (entities.isEmpty() || indexes.isEmpty()) return;

        for (SecondaryIndex<ID> index : indexes.values()) {
            FieldModel<T> field = repositoryModel.fieldByName(index.field());
            Map<Object, Set<ID>> removals = collectValueToIds(entities, field);

            for (Map.Entry<Object, Set<ID>> entry : removals.entrySet()) {
                Set<ID> existing = index.map().get(entry.getKey());
                if (existing == null) continue;

                existing.removeAll(entry.getValue());
                if (existing.isEmpty()) index.map().remove(entry.getKey());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("ObjectAllocationInLoop")
    private Map<Object, Set<ID>> collectValueToIds(Collection<T> entities, FieldModel<T> field) {
        Map<Object, Set<ID>> map = new HashMap<>(entities.size());
        for (T entity : entities) {
            try {
                Object value = field.getValue(entity);
                ID id        = repositoryModel.getPrimaryKeyValue(entity);
                map.computeIfAbsent(value, k -> new HashSet<>(32)).add(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return map;
    }

    private void persistIndex(SecondaryIndex<ID> index) throws IOException {
        Files.createDirectories(indexRoot.getParent());
        objectMapper
            .createWriter()
            .write(objectMapper.writeValue(index), indexRoot);
    }
}