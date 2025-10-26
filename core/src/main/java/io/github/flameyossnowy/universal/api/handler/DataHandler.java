package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

/**
 * Handles reading from and writing to the database for a specific type.
 * 
 * @param <T> the type this handler is responsible for
 */
public interface DataHandler<T> {
    
    /**
     * Gets the type this handler is responsible for.
     * 
     * @return the type class
     */
    Class<T> getType();
    
    /**
     * Gets the database type this handler uses for storage.
     * 
     * @return the database type class
     */
    Class<?> getDatabaseType();
    
    /**
     * Reads a value from the database result.
     * 
     * @param result the database result
     * @param columnName the column name to read from
     * @return the read value, or null if the value is NULL
     */
    T fromDatabase(DatabaseResult result, String columnName);
    
    /**
     * Writes a value to the database parameters.
     * 
     * @param parameters the database parameters
     * @param index the parameter index
     * @param value the value to write, may be null
     */
    void toDatabase(DatabaseParameters parameters, String index, T value);
    
    /**
     * Gets the SQL type constant for this handler.
     * 
     * @return the SQL type constant from {@link java.sql.Types}
     */
    int getSqlType();
    
    /**
     * Creates a new DataHandler for the specified type and database type.
     * 
     * @param <T> the type to handle
     * @param type the type class
     * @param databaseType the database type class
     * @param sqlType the SQL type constant from {@link java.sql.Types}
     * @param reader function to read from database
     * @param writer function to write to database
     * @return a new DataHandler instance
     */
    static <T> DataHandler<T> of(Class<T> type, Class<?> databaseType, int sqlType,
                               DatabaseReader<T> reader, DatabaseWriter<T> writer) {
        return new DataHandler<>() {
            @Override public Class<T> getType() { return type; }
            @Override public Class<?> getDatabaseType() { return databaseType; }
            @Override public int getSqlType() { return sqlType; }
            @Override public T fromDatabase(DatabaseResult result, String columnName) { return reader.read(result, columnName); }
            @Override public void toDatabase(DatabaseParameters parameters, String index, T value) { writer.write(parameters, index, value); }
        };
    }
    
    /**
     * Functional interface for reading from a database result.
     * 
     * @param <T> the type to read
     */
    @FunctionalInterface
    interface DatabaseReader<T> {
        T read(DatabaseResult result, String columnName);
    }
    
    /**
     * Functional interface for writing to database parameters.
     * 
     * @param <T> the type to write
     */
    @FunctionalInterface
    interface DatabaseWriter<T> {
        void write(DatabaseParameters parameters, String index, T value);
    }
}
