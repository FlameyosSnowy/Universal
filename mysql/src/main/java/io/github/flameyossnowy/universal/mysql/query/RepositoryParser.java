package io.github.flameyossnowy.universal.mysql.query;

import io.github.flameyossnowy.universal.api.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.annotations.Constraint;
import io.github.flameyossnowy.universal.api.annotations.References;
import io.github.flameyossnowy.universal.api.repository.RepositoryMetadata;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.resolvers.MySQLValueTypeResolver;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class RepositoryParser {
    public static @NotNull String read(@NotNull RepositoryMetadata.RepositoryInformation metadata, MySQLRepositoryAdapter<?> repository) {
        StringJoiner joiner = new StringJoiner(", ", "CREATE TABLE IF NOT EXISTS " + metadata.repository() + " (", ")");
        generateColumns(metadata, joiner, repository);

        String classConstraints = processClassLevelConstraints(metadata);
        if (!classConstraints.isEmpty()) joiner.add(classConstraints);

        System.out.println(joiner);
        return joiner.toString();
    }

    @Contract(pure = true)
    private static void generateColumns(@NotNull RepositoryMetadata.RepositoryInformation metadata, StringJoiner joiner, MySQLRepositoryAdapter<?> repository) {
        List<Result> primaryKey = new ArrayList<>();
        List<Result> uniqueKeys = new ArrayList<>();

        for (RepositoryMetadata.FieldData data : metadata.fields()) {
            Result result = generateColumn(data, repository);
            joiner.add(result.column);
            if (result.hasPrimaryKey) primaryKey.add(result);
            if (result.unique) uniqueKeys.add(result);
        }

        if (primaryKey.isEmpty()) throw new RuntimeException("No primary key defined for entity: " + metadata.repository());

        StringJoiner primaryKeys = new StringJoiner(", ");
        for (Result result : primaryKey) primaryKeys.add(result.name);
        joiner.add("PRIMARY KEY (" + primaryKeys + ")");

        if (!uniqueKeys.isEmpty() && metadata.constraints().length == 0) {
            StringJoiner uniqueFields = new StringJoiner(", ");
            for (Result result : uniqueKeys) uniqueFields.add(result.name);
            joiner.add("UNIQUE (" + uniqueFields + ")");
        }
    }

    private static String processClassLevelConstraints(@NotNull RepositoryMetadata.RepositoryInformation metadata) {
        StringJoiner joiner = new StringJoiner(", ");

        for (Constraint constraint : metadata.constraints()) {
            String constraintName = constraint.name();
            String[] fields = constraint.fields();
            if (fields.length == 0) continue;

            List<String> uniqueFields = new ArrayList<>();
            List<String> checkConditions = new ArrayList<>();

            processConstraints0(metadata, fields, checkConditions, uniqueFields, joiner, constraintName);
        }
        return joiner.toString();
    }

    private static void processConstraints0(final @NotNull RepositoryMetadata.RepositoryInformation metadata, final String @NotNull [] fields, final List<String> checkConditions, final List<String> uniqueFields, final StringJoiner joiner, final String constraintName) {
        for (String fieldName : fields) {
            RepositoryMetadata.FieldData fieldData = metadata.fieldData().get(fieldName);
            if (fieldData == null) continue;

            if (fieldData.condition() != null) checkConditions.add(fieldData.condition().value());
            if (fieldData.unique()) uniqueFields.add(fieldName);
        }

        if (!checkConditions.isEmpty()) {
            joiner.add("CONSTRAINT " + constraintName + " CHECK (" + String.join(" AND ", checkConditions) + ")");
        }

        if (!uniqueFields.isEmpty()) {
            joiner.add("CONSTRAINT " + constraintName + " UNIQUE (" + String.join(", ", uniqueFields) + ")");
        }
    }

    private static @NotNull Result generateColumn(@NotNull RepositoryMetadata.FieldData data, @NotNull MySQLRepositoryAdapter<?> repository) {
        StringBuilder fieldBuilder = new StringBuilder();
        boolean primaryKey = data.primary();
        boolean unique = data.unique();

        String name = data.name();
        fieldBuilder.append(name).append(' ').append(resolveType(data, repository));

        if (data.nonNull()) fieldBuilder.append(" NOT NULL");
        if (data.autoIncrement()) fieldBuilder.append(" AUTO_INCREMENT");
        if (data.condition() != null) fieldBuilder.append(" CHECK (").append(data.condition().value()).append(")");
        if (unique) fieldBuilder.append(" UNIQUE");

        if (data.foreign() != null && data.references() != null) {
            appendForeignKeyConstraints(data, fieldBuilder);
        }

        return new Result(primaryKey, unique, name, fieldBuilder.toString());
    }

    private static String resolveType(@NotNull RepositoryMetadata.FieldData data, @NotNull MySQLRepositoryAdapter<?> repository) {
        MySQLValueTypeResolver resolver = repository.getValueTypeResolverRegistry().getResolver(data.type());

        if (resolver == null && data.resolver() != null) {
            resolver = (MySQLValueTypeResolver) ReflectiveMetaData.newInstance(data.resolver().value());
            repository.getValueTypeResolverRegistry().register(data.type(), resolver);
        }

        return repository.getValueTypeResolverRegistry().getType(
                Objects.requireNonNull(resolver, "Unknown type: " + data.type() + " and no resolver provided."));
    }

    private static void appendForeignKeyConstraints(@NotNull RepositoryMetadata.FieldData data, StringBuilder fieldBuilder) {
        References references = data.references();
        if (references.field().isEmpty()) throw new RuntimeException("FOREIGN KEY constraint requires referenced table and field.");

        fieldBuilder.append(" REFERENCES ").append(references.table()).append(" (").append(references.field()).append(")");

        if (data.onDelete() != null) fieldBuilder.append(" ON DELETE ").append(data.onDelete().action());
        if (data.onUpdate() != null) fieldBuilder.append(" ON UPDATE ").append(data.onUpdate().action());
    }

    private record Result(boolean hasPrimaryKey, boolean unique, String name, String column) {}
}