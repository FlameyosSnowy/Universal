package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public sealed interface Query permits DeleteQuery, SelectQuery, UpdateQuery {
    @Contract("_ -> new")
    static SelectQuery.@NotNull SelectQueryBuilder select(String... columns) {
        return new SelectQuery.SelectQueryBuilder(columns);
    }

    @Contract(" -> new")
    static DeleteQuery.@NotNull DeleteQueryBuilder delete() {
        return new DeleteQuery.DeleteQueryBuilder();
    }

    @Contract(" -> new")
    static UpdateQuery.@NotNull UpdateQueryBuilder update() {
        return new UpdateQuery.UpdateQueryBuilder();
    }
}
