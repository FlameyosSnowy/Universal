package io.github.flameyossnowy.universal.sql.internals.query;

import io.github.flameyossnowy.universal.api.options.SortOption;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

public final class SqlSortBuilder {
    public String buildSortOptions(@NotNull Iterable<SortOption> sortOptions) {
        StringJoiner joiner = new StringJoiner(", ");
        for (SortOption sortOption : sortOptions) {
            joiner.add(sortOption.field() + " " + (sortOption.order() == SortOrder.ASCENDING ? "ASC" : "DESC"));
        }
        return joiner.toString();
    }
}
