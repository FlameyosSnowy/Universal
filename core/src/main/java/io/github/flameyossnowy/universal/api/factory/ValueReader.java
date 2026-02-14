package io.github.flameyossnowy.universal.api.factory;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

/**
 * Reads values from a database result set in a type-safe manner.
 * <p>
 * This interface provides methods for reading primitive types directly,
 * as well as a generic method that uses TypeResolverRegistry for complex types.
 */
public interface ValueReader<ID> {
    /**
     * Reads a value of the specified type at the given index.
     * <p>
     * This method uses the TypeResolverRegistry to resolve and convert
     * the database value to the requested application type.
     *
     * @param <T>   The type to read
     * @param index The 0-based index of the value
     * @return The value at the specified index, or null if the value is NULL
     */
    <T> T read(int index);

    ID getId();

    DatabaseResult getDatabaseResult();
}