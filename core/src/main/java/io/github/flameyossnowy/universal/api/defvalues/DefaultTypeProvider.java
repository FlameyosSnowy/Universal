package io.github.flameyossnowy.universal.api.defvalues;

public interface DefaultTypeProvider<T> {
    T supply();

    Class<T> getType();
}
