package io.github.flameyossnowy.universal.sql.resolvers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface SQLValueTypeResolver<T> {
    /**
     * Resolves a value of type T from a given {@link ResultSet}
     * by using the given column label.
     *
     * @param resultSet the result set to read from
     * @param columnLabel the column label to read from
     * @return the resolved value of type T
     * @throws Exception if a problem occurs during resolution
     */
    T resolve(ResultSet resultSet, String columnLabel) throws Exception;

    /**
     * Inserts a value of type T into a given {@link PreparedStatement}
     * at the given parameter index.
     *
     * @param statement the statement to insert into
     * @param parameterIndex the index at which to insert the value
     * @param value the value of type T to insert
     * @throws Exception if a problem occurs during insertion
     */
    void insert(PreparedStatement statement, int parameterIndex, T value) throws Exception;

    /**
     * Gets the type of the value that is actually stored in the database.
     * This is usually the type of the database column that the value is stored in.
     *
     * @return the type of the value that is stored in the database
     */
    Class<?> encodedType();
}

