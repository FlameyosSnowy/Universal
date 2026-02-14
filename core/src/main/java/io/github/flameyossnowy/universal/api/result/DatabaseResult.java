package io.github.flameyossnowy.universal.api.result;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;

/**
 * Represents a database-agnostic result set that can be used to retrieve values
 * from a query result without depending on any specific database implementation.
 */
@SuppressWarnings("unused")
public interface DatabaseResult {
    CollectionHandler getCollectionHandler();

    boolean supportsArraysNatively();

    RepositoryModel<?, ?> repositoryModel();

    /**
     * Gets a value from the result by column name.
     * 
     * @param columnName the name of the column
     * @param type the expected type of the value
     * @return the value from the result set
     * @throws IllegalArgumentException if the column doesn't exist or type conversion fails
     */
    <T> T get(String columnName, Class<T> type);
    
    /**
     * Checks if a column exists in the result.
     * 
     * @param columnName the name of the column to check
     * @return true if the column exists, false otherwise
     */
    boolean hasColumn(String columnName);
    
    /**
     * Gets the column count in the result.
     * 
     * @return the number of columns in the result
     */
    int getColumnCount();
    
    /**
     * Gets the column name for the specified index.
     * 
     * @param columnIndex the 1-based index of the column
     * @return the column name
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    String getColumnName(int columnIndex);
}
