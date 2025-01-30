package me.flame.universal.mysql.query;

import me.flame.universal.api.options.SelectOption;
import me.flame.universal.api.options.UpdateQuery;
import me.flame.universal.api.repository.RepositoryMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

public class UpdateQueryParser {
    public static @NotNull String parse(UpdateQuery query, Class<?> repository) {
        String tableName = RepositoryMetadata.getMetadata(repository).repository();
        String setClause = generateSetClause(query);

        if (query.getConditions() == null || query.getConditions().isEmpty()) {
            return String.format("UPDATE %s SET %s", tableName, setClause);
        }

        String whereClause = generateConditionClause(query);

        return String.format("UPDATE %s SET %s WHERE %s", tableName, setClause, whereClause);
    }

    private static @NotNull String generateConditionClause(final @NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (SelectOption selectOption : query.getConditions()) joiner.add(selectOption.option() + " " + selectOption.operator() + " ?");
        return joiner.toString();
    }

    private static @NotNull String generateSetClause(final @NotNull UpdateQuery query) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : query.getUpdates().keySet()) joiner.add(key + " = ?");
        return joiner.toString();
    }
}