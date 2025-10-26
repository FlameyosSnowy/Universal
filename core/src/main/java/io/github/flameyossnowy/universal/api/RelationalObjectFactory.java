package io.github.flameyossnowy.universal.api;

import io.github.flameyossnowy.universal.api.factory.DatabaseObjectFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Interface for SQL object factories.
 * @param <T> the element class
 * @param <ID> the id class
 */
public interface RelationalObjectFactory<T, ID> extends DatabaseObjectFactory<T, ResultSet> {
    /**
     * Inserts a single entity into the database using the given PreparedStatement.
     *
     * @param statement The prepared statement to be used to insert the entity.
     * @param entity    The entity to be inserted.
     * @throws Exception If an error occurs.
     */
    void insertEntity(PreparedStatement statement, T entity) throws Exception;

    /**
     * Inserts all collection fields of the given entity using the given PreparedStatement.
     * This method is called after the primary entity has been inserted and the ID of the primary
     * entity has been set.
     *
     * @param entity    The entity to be inserted.
     * @param id        The ID of the primary entity.
     * @param statement The prepared statement to be used to insert the collections.
     * @throws Exception If an error occurs.
     */
    void insertCollectionEntities(T entity, ID id, PreparedStatement statement) throws Exception;
}
