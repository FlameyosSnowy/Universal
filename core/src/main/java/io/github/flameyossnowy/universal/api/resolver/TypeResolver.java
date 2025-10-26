package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

/**
 * Converts between application types and database types in a database-agnostic way.
 * 
 * @param <T> the application type
 */
public interface TypeResolver<T> {
    
    /**
     * Gets the application type that this resolver handles.
     * 
     * @return the application type class
     */
    Class<T> getType();
    
    /**
     * Gets the database type that this resolver uses for storage.
     * 
     * @return the database type class
     */
    Class<?> getDatabaseType();
    
    /**
     * Converts a database result to an application type.
     * 
     * @param result the database result
     * @param columnName the name of the column to read
     * @return the converted value
     */
    T resolve(DatabaseResult result, String columnName);
    
    /**
     * Converts an application type to a database parameter.
     * 
     * @param parameters the parameters to set the value in
     * @param index the 1-based parameter index
     * @param value the value to convert
     */
    void insert(DatabaseParameters parameters, String index, T value);
    
    /**
     * Creates a resolver that delegates to a DataHandler.
     * 
     * @param <T> the application type
     * @param handler the data handler to delegate to
     * @return a new TypeResolver that uses the specified handler
     */
    static <T> TypeResolver<T> fromHandler(DataHandler<T> handler) {
        return new TypeResolver<>() {
            @Override public Class<T> getType() { return handler.getType(); }
            @Override public Class<?> getDatabaseType() { return handler.getDatabaseType(); }
            @Override public T resolve(DatabaseResult result, String columnName) { return handler.fromDatabase(result, columnName); }
            @Override public void insert(DatabaseParameters parameters, String index, T value) { handler.toDatabase(parameters, index, value); }
        };
    }
    
    /**
     * Creates a resolver for an enum type.
     * 
     * @param <E> the enum type
     * @param enumClass the enum class
     * @return a resolver that converts enums to/from strings
     */
    static <E extends Enum<E>> TypeResolver<E> forEnum(Class<E> enumClass) {
        return fromHandler(DataHandler.of(
            enumClass,
            String.class,
            java.sql.Types.VARCHAR,
            (result, columnName) -> {
                String value = result.get(columnName, String.class);
                return value != null ? Enum.valueOf(enumClass, value) : null;
            },
            (parameters, index, value) -> 
                parameters.set(index, value != null ? value.name() : null, String.class)
        ));
    }
}
