package io.github.flameyossnowy.universal.api.params;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a database-agnostic way to set parameters for a query
 * without depending on any specific database implementation.
 */
@SuppressWarnings("unused")
public interface DatabaseParameters {
    /**
     * Sets a parameter value by name.
     * 
     * @param name the name of the parameter
     * @param value the value to set
     * @param type the type of the value
     * @param <T> the type of the value
     * @throws IllegalArgumentException if the type is not supported
     */
    <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type);
    
    /**
     * Sets a parameter to NULL by name.
     * 
     * @param name the name of the parameter
     * @param type the expected type of the parameter
     */
    void setNull(@NotNull String name, @NotNull Class<?> type);
    
    /**
     * Gets the number of parameters that have been set.
     * 
     * @return the number of parameters
     */
    int size();
    
    /**
     * Gets a parameter value by index.
     * 
     * @param index the 1-based index of the parameter
     * @param type the expected type of the parameter
     * @param <T> the type of the value to return
     * @return the parameter value, or null if not set
     * @throws IllegalArgumentException if the type is not supported
     */
    @Nullable
    <T> T get(int index, @NotNull Class<T> type);
    
    /**
     * Gets a parameter value by name.
     * 
     * @param name the name of the parameter
     * @param type the expected type of the parameter
     * @param <T> the type of the value to return
     * @return the parameter value, or null if not set
     * @throws IllegalArgumentException if the type is not supported
     */
    @Nullable
    <T> T get(@NotNull String name, @NotNull Class<T> type);
    
    /**
     * Checks if a parameter exists by name.
     * 
     * @param name the name of the parameter
     * @return true if the parameter exists, false otherwise
     */
    boolean contains(@NotNull String name);
}
