package io.github.flameyossnowy.universal.sqlite.query;

import io.github.flameyossnowy.universal.api.repository.RepositoryMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.StringJoiner;

public class InsertQueryParser {
    public static @NotNull String parse(RepositoryMetadata.RepositoryInformation information, @NotNull Collection<RepositoryMetadata.FieldData> fields) {
        StringJoiner columnJoiner = new StringJoiner(", ");
        for (RepositoryMetadata.FieldData data : fields) {
            if (data.autoIncrement()) continue;
            columnJoiner.add(data.name());
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)", information.repository(), columnJoiner, generatePlaceholders(fields.size()));
    }

    private static String generatePlaceholders(final int size) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < size; i++) joiner.add("?");
        return joiner.toString();
    }
}