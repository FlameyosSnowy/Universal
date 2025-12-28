package io.github.flameyossnowy.universal.api.defvalues;

/**
 * A dynamic default type provider to provide a default value for a type.
 * @param <T> The type of the default value.
 * @author flameyosflow
 * @version 5.0.0
 */
public interface DefaultTypeProvider<T> {
    /**
     * Supplies a default value of type {@link #getType()}.
     * @return A default value of type {@link #getType()}.
     */
    T supply();

    /**
     * Retrieves the class type of the generic type {@code T}.
     *
     * @return The {@link Class} object representing the type {@code T}.
     */
    Class<T> getType();
}
