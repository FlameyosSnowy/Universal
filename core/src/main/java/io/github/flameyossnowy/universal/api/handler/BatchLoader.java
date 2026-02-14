package io.github.flameyossnowy.universal.api.handler;

public interface BatchLoader<ID> {
    void loadAll(LazyBatchContext<ID> ctx);
}
