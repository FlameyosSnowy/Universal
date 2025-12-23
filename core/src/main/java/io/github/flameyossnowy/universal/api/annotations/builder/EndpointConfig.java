package io.github.flameyossnowy.universal.api.annotations.builder;

import io.github.flameyossnowy.universal.api.annotations.enums.HttpMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record EndpointConfig(String findAll, String findById, String create, String update, String delete,
                             HttpMethod updateMethod) {
    public EndpointConfig(
            String findAll,
            String findById,
            String create,
            String update,
            String delete, HttpMethod updateMethod) {
        this.findAll = findAll.isEmpty() ? "" : findAll;
        this.findById = findById;
        this.create = create.isEmpty() ? "" : create;
        this.update = update;
        this.delete = delete;
        this.updateMethod = updateMethod;
    }

    @SuppressWarnings("HardcodedFileSeparator") // not a file :P
    @NotNull
    @Contract(value = " -> new", pure = true)
    public static EndpointConfig defaults() {
        return new EndpointConfig("", "/{id}", "", "/{id}", "/{id}", HttpMethod.PUT);
    }
}