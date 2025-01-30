package me.flame.universal.api.options;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class Query {
    @Contract("_ -> new")
    public static SelectQuery.@NotNull SelectQueryBuilder select(String... columns) {
        return new SelectQuery.SelectQueryBuilder(columns);
    }

    @Contract(" -> new")
    public static InsertQuery.@NotNull InsertQueryBuilder insert() {
        return new InsertQuery.InsertQueryBuilder();
    }

    @Contract(" -> new")
    public static DeleteQuery.@NotNull DeleteQueryBuilder delete() {
        return new DeleteQuery.DeleteQueryBuilder();
    }

    @Contract(" -> new")
    public static UpdateQuery.@NotNull UpdateQueryBuilder update() {
        return new UpdateQuery.UpdateQueryBuilder();
    }
}
