package me.flame.universal.sqlite.query;

import me.flame.universal.api.options.SelectOption;
import me.flame.universal.api.options.SelectQuery;
import me.flame.universal.api.options.SortOption;
import me.flame.universal.api.options.SortOrder;
import me.flame.universal.api.repository.RepositoryMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

public class SelectQueryParser {
    public static @NotNull String parse(SelectQuery query, Class<?> repository) {
        String tableName = RepositoryMetadata.getMetadata(repository).repository();
        if (query == null) return "SELECT * FROM " + tableName;

        String columns = query.columns().isEmpty() ? "*" : String.join(", ", query.columns());
        String filters = query.filters().isEmpty() ? "" : "WHERE " + buildConditions(query.filters());
        String sortOptions = query.sortOptions().isEmpty() ? "" : "ORDER BY " + buildSortOptions(query.sortOptions());

        return String.format("SELECT %s FROM %s %s %s %s", columns, tableName, filters, sortOptions, query.limit() == -1 ? "" : "LIMIT " + query.limit());
    }

    private static String buildConditions(@NotNull Iterable<SelectOption> filters) {
        StringJoiner joiner = new StringJoiner(" AND ");
        for (SelectOption filter : filters) joiner.add(filter.option() + " " + filter.operator() + " ?");
        return joiner.toString();
    }

    private static String buildSortOptions(@NotNull Iterable<SortOption> sortOptions) {
        StringJoiner joiner = new StringJoiner(", ");
        for (SortOption sortOption : sortOptions) joiner.add(sortOption.field() + " " + (sortOption.order() == SortOrder.ASCENDING ? "ASC" : "DESC"));
        return joiner.toString();
    }
}