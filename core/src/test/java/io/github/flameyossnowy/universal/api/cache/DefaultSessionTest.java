package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultSessionTest {

    static final class MapSessionCache<ID, T> implements SessionCache<ID, T> {
        private final Map<ID, T> backing = new LinkedHashMap<>();
        private final CacheStatistics statistics = new CacheStatistics();

        @Override public Map<ID, T> getInternalCache() { return backing; }
        @Override public T get(ID id) { return backing.get(id); }
        @Override public T put(ID id, T value) { return backing.put(id, value); }
        @Override public T remove(ID id) { return backing.remove(id); }
        @Override public void clear() { backing.clear(); }
        @Override public int size() { return backing.size(); }
        @Override public CacheStatistics getStatistics() { return statistics; }
        @Override public CacheMetrics getMetrics() { return statistics.getMetrics(); }
    }

    static final class Entity {
        final int id;
        Entity(int id) { this.id = id; }
        @Override public String toString() { return "Entity{" + id + "}"; }
    }

    @Test
    void findByIdUsesCacheWhenEnabled() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        Entity e1 = new Entity(1);
        when(repo.findById(1)).thenReturn(e1);

        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.noneOf(SessionOption.class));

        assertSame(e1, session.findById(1));
        assertSame(e1, session.findById(1));
        verify(repo, times(1)).findById(1);
    }

    @Test
    void findByIdSkipsCacheWhenNoCacheOptionSet() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        cache.put(1, new Entity(1));

        Entity fromRepo = new Entity(1);
        when(repo.findById(1)).thenReturn(fromRepo);

        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.of(SessionOption.NO_CACHE));

        assertSame(fromRepo, session.findById(1));
        verify(repo, times(1)).findById(1);
    }

    @Test
    void findAllByIdReturnsCacheHitsAndFetchesMissingInSingleCall() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        Entity cached = new Entity(1);
        cache.put(1, cached);

        Entity fetched = new Entity(2);
        when(repo.findAllById(eq(List.of(2)))).thenReturn(Map.of(2, fetched));

        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.noneOf(SessionOption.class));

        Map<Integer, Entity> result = session.findAllById(List.of(1, 2));

        assertEquals(2, result.size());
        assertSame(cached, result.get(1));
        assertSame(fetched, result.get(2));
        assertSame(fetched, cache.get(2));

        verify(repo, times(1)).findAllById(eq(List.of(2)));
        verify(repo, never()).findById(any());
    }

    @Test
    void bufferedInsertDoesNotCallRepositoryUntilCommit() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);
        when(model.getPrimaryKeyValue(any())).thenAnswer(inv -> ((Entity) inv.getArgument(0)).id);

        when(repo.insert(any(), eq(tx))).thenReturn(TransactionResult.success(true));
        when(tx.commit()).thenReturn(TransactionResult.success(true));

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.of(SessionOption.BUFFERED_WRITE));

        Entity e = new Entity(1);
        session.insert(e);

        verify(repo, never()).insert(any(), any());
        assertEquals(1, session.getPendingOperationCount());

        session.commit();

        verify(repo, times(1)).insert(eq(e), eq(tx));
        verify(tx, times(1)).commit();
        assertSame(e, cache.get(1));
        assertEquals(0, session.getPendingOperationCount());
    }

    @Test
    void commitRollsBackAndReturnsFailureIfAnyOperationErrors() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);
        when(model.getPrimaryKeyValue(any())).thenAnswer(inv -> ((Entity) inv.getArgument(0)).id);

        RuntimeException opErr = new RuntimeException("op failed");
        when(repo.insert(any(), eq(tx))).thenReturn(TransactionResult.failure(opErr));

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.of(SessionOption.BUFFERED_WRITE));

        Entity e = new Entity(1);
        session.insert(e);

        TransactionResult<Boolean> commit = session.commit();
        assertTrue(commit.isError());
        assertSame(opErr, commit.getError().orElseThrow());

        verify(tx, never()).commit();
        try {
            verify(tx, times(1)).rollback();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        assertNull(cache.get(1));
    }

    @Test
    void rollbackRunsCallbacksAndWrapsRollbackException() throws Exception {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);
        when(model.getPrimaryKeyValue(any())).thenAnswer(inv -> ((Entity) inv.getArgument(0)).id);

        when(repo.insert(any(), eq(tx))).thenReturn(TransactionResult.success(true));

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.noneOf(SessionOption.class));

        Entity e = new Entity(1);
        session.insert(e);
        assertSame(e, cache.get(1));

        doThrow(new Exception("rollback failed")).when(tx).rollback();

        RuntimeException thrown = assertThrows(RuntimeException.class, session::rollback);
        assertNotNull(thrown.getCause());
        assertEquals("rollback failed", thrown.getCause().getMessage());
        assertNull(cache.get(1));
    }

    @Test
    void deleteRollbackRestoresPreviousEntity() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);
        when(model.getPrimaryKeyValue(any())).thenAnswer(inv -> ((Entity) inv.getArgument(0)).id);

        Entity previous = new Entity(1);
        when(repo.findById(1)).thenReturn(previous);
        when(repo.delete((Entity) any(), eq(tx))).thenReturn(TransactionResult.success(false));

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.noneOf(SessionOption.class));

        Entity toDelete = previous;
        session.delete(toDelete);

        assertNull(cache.get(1));

        session.rollback();

        assertSame(previous, cache.get(1));
    }

    @Test
    void updateRollbackRestoresPreviousEntity() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);
        when(model.getPrimaryKeyValue(any())).thenAnswer(inv -> ((Entity) inv.getArgument(0)).id);

        Entity previous = new Entity(1);
        when(repo.findById(1)).thenReturn(previous);

        Entity updated = new Entity(1);
        when(repo.updateAll(eq(updated), eq(tx))).thenReturn(TransactionResult.success(false));

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.noneOf(SessionOption.class));

        session.update(updated);
        assertSame(updated, cache.get(1));

        session.rollback();
        assertSame(previous, cache.get(1));
    }

    @Test
    void closeClearsCacheAndClosesTransactionContext() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);

        MapSessionCache<Integer, Entity> cache = new MapSessionCache<>();
        cache.put(1, new Entity(1));

        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, cache, 1L, EnumSet.noneOf(SessionOption.class));
        session.close();

        verify(tx, times(1)).close();
        assertEquals(0, cache.size());
    }

    @Test
    void findAllByIdAsyncDelegatesToFindAllById() {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Entity, Integer, Object> repo = mock(RepositoryAdapter.class);
        @SuppressWarnings("unchecked")
        RepositoryModel<Entity, Integer> model = mock(RepositoryModel.class);
        @SuppressWarnings("unchecked")
        TransactionContext<Object> tx = mock(TransactionContext.class);

        when(repo.beginTransaction()).thenReturn(tx);
        when(repo.getRepositoryModel()).thenReturn(model);

        AtomicInteger calls = new AtomicInteger();
        when(repo.findAllById(eq(List.of(1)))).thenAnswer(inv -> {
            calls.incrementAndGet();
            return Map.of(1, new Entity(1));
        });

        DefaultSession<Integer, Entity, Object> session = new DefaultSession<>(repo, new MapSessionCache<>(), 1L, EnumSet.of(SessionOption.NO_CACHE));

        Map<Integer, Entity> result = session.findAllByIdAsync(List.of(1)).join();
        assertEquals(1, calls.get());
        assertEquals(1, result.size());
    }
}
