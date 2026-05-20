package io.github.flameyossnowy.universal.checker.schema;

import io.github.flameyossnowy.universal.checker.RepositoryModel;
import io.github.flameyossnowy.universal.checker.FieldModel;

import java.util.*;

public record RepositorySchema(
    String name,
    Map<String, FieldSchema> fields,
    Set<String> primaryKeys
) {
    private static FieldType infer(String type) {
        return switch (type) {
            case "java.lang.String" -> FieldType.STRING;

            case "int", "java.lang.Integer",
                 "long", "java.lang.Long",
                 "double", "java.lang.Double",
                 "float", "java.lang.Float",
                 "short", "java.lang.Short",
                 "byte", "java.lang.Byte" -> FieldType.NUMBER;

            case "java.lang.Boolean", "boolean" -> FieldType.BOOLEAN;

            case "java.util.UUID", "java.lang.UUID" -> FieldType.UUID;

            case "java.time.LocalDate",
                 "java.time.LocalDateTime",
                 "java.time.Instant",
                 "java.time.ZonedDateTime",
                 "java.time.OffsetDateTime",
                 "java.util.Date",
                 "java.sql.Timestamp" -> FieldType.TEMPORAL;

            default -> FieldType.UNKNOWN;
        };
    }

    public static RepositorySchema from(RepositoryModel model) {
        Map<String, FieldSchema> fields = new HashMap<>(model.fields().size() * 2);

        for (FieldModel f : model.fields()) {
            fields.put(f.name(), fields.put(f.name(), new FieldSchema(
                f.name(),
                f.typeQualifiedName(),
                f.nullable(),
                f.indexed(),
                f.relationship(),
                f.collectionKind(),
                infer(f.typeQualifiedName())
            )));
        }

        Set<String> ids = new HashSet<>();
        for (FieldModel pk : model.primaryKeys()) {
            ids.add(pk.name());
        }

        return new RepositorySchema(
            model.tableName(),
            fields,
            Collections.unmodifiableSet(ids)
        );
    }

    public FieldSchema field(String name) {
        FieldSchema f = fields.get(name);
        if (f == null) throw new IllegalArgumentException("Unknown field: " + name);
        return f;
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }
}