package io.github.flameyossnowy.universal.api.factory;

import io.github.flameyossnowy.universal.api.annotations.*;

public interface DatabaseObjectFactory<T, ID, S> {
    /**
     * Creates a new instance of the entity represented by this factory.
     *
     * @param set the data source that the entity should be created from
     * @return a new instance of the entity
     * @throws Exception if an exception occurs while creating the entity
     */
    T create(S set) throws Exception;

    /**
     * Creates a new instance of the entity represented by this factory,
     * resolving all relationships specified by {@link OneToOne},
     * {@link OneToMany}, and {@link ManyToOne} annotations.
     *
     * @param set the data source that the entity should be created from
     * @return a new instance of the entity, with all relationships resolved
     * @throws Exception if an exception occurs while creating the entity
     */
    T createWithRelationships(S set) throws Exception;
}
