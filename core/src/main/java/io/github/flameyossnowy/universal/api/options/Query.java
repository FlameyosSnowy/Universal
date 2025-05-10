package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public sealed interface Query permits DeleteQuery, SelectQuery, UpdateQuery {
    /**
     * Create a new select query builder with the given columns.
     *
     * @param columns the columns to select
     * @return a new select query builder
     */
    @Contract("_ -> new")
    static SelectQuery.@NotNull SelectQueryBuilder select(String... columns) {
        return new SelectQuery.SelectQueryBuilder(columns);
    }


    /**
     * Create a new delete query builder.
     * <p>
     * This method is marked as obsolete as better apis exist
     * <p>
     * <b>Use {@link io.github.flameyossnowy.universal.api.RepositoryAdapter#delete(Object)} instead</b>
     *
     * @return a new delete query builder
     */
    @Contract(" -> new")
    @ApiStatus.Obsolete
    static DeleteQuery.@NotNull DeleteQueryBuilder delete() {
        return new DeleteQuery.DeleteQueryBuilder();
    }

    /**
     * Create a new update query builder.
     * <p>
     * This method is marked as obsolete as better apis exist
     * <p>
     * <b>Use {@link io.github.flameyossnowy.universal.api.RepositoryAdapter#updateAll(Object)} (Object)} instead</b>
     *
     * @return a new update query builder
     */
    @Contract(" -> new")
    @ApiStatus.Obsolete
    static UpdateQuery.@NotNull UpdateQueryBuilder update() {
        return new UpdateQuery.UpdateQueryBuilder();
    }
}
