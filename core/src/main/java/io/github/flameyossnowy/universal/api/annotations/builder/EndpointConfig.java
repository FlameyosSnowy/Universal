package io.github.flameyossnowy.universal.api.annotations.builder;

import io.github.flameyossnowy.universal.api.annotations.enums.HttpMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for endpoints.
 * @param findAll API link for all elements.
 * @param findById API link for an element with an ID.
 * @param create API link to create/insert an element.
 * @param update API link to update an element.
 * @param delete API link to delete an element
 * @param updateMethod PUT or PATCH.
 * @author flameyosflow
 * @version 6.0.0
 */
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