package io.github.flameyossnowy.universal.mongodb.parsers;

import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;
import io.github.flameyossnowy.universal.api.ReflectiveMetaData;
import io.github.flameyossnowy.universal.api.repository.RepositoryMetadata;
import io.github.flameyossnowy.universal.mongodb.resolvers.MongoValueTypeResolver;
import io.github.flameyossnowy.universal.mongodb.resolvers.ValueTypeResolverRegistry;
import me.sunlan.fastreflection.FastField;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@SuppressWarnings("unchecked")
public class MongoDatabaseParser {
    private final ValueTypeResolverRegistry resolverRegistry;
    private final RepositoryMetadata.RepositoryInformation repository;
    private final String collectionName;
    private final List<MongoCondition> conditions = new ArrayList<>();
    private final Set<String> uniqueFields = new HashSet<>();
    private final Set<String> nonNullFields = new HashSet<>();

    public MongoDatabaseParser(final ValueTypeResolverRegistry resolverRegistry, final Class<?> repository) {
        this.resolverRegistry = resolverRegistry;
        this.repository = RepositoryMetadata.getMetadata(repository);
        this.collectionName = extractCollectionName(repository);
        extractMetadata();
    }

    public <T> Document toDocument(T object) {
        Collection<RepositoryMetadata.FieldData> fields = repository.fields();

        Document document = new Document();
        for (RepositoryMetadata.FieldData entry : fields) {
            String name = entry.name();
            FastField field = entry.field();

            Class<?> type = entry.rawField().getType();
            MongoValueTypeResolver resolver = resolverRegistry.getResolver(type);

            try {
                resolver.insert(document, name, ReflectiveMetaData.getFieldValue(object, field));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return document;
    }

    public <T> T fromDocument(Document document) {
        T instance = (T) ReflectiveMetaData.newInstance(repository);

        try {
            buildFields(document, instance);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return instance;
    }

    private <T> void buildFields(final Document set, final T instance) throws Throwable {
        Collection<RepositoryMetadata.FieldData> data = repository.fields();
        for (RepositoryMetadata.FieldData entry : data) {
            String name = entry.name();
            FastField field = entry.field();

            Class<?> type = entry.rawField().getType();
            MongoValueTypeResolver resolver = resolverRegistry.getResolver(type);

            Object value = resolver.resolve(set, name);
            if (value != null) field.set(instance, value);
        }
    }

    private String extractCollectionName(Class<?> entityClass) {
        RepositoryMetadata.RepositoryInformation metadata = RepositoryMetadata.getMetadata(entityClass);
        return metadata.repository();
    }

    private void extractMetadata() {
        for (RepositoryMetadata.FieldData field : repository.fields()) {
            if (field.unique()) {
                uniqueFields.add(field.name());
            }
            if (field.nonNull()) {
                nonNullFields.add(field.name());
            }
            if (field.condition() != null) {
                conditions.add(new MongoCondition(field.name(), field.condition().value()));
            }
            if (field.resolver() != null) {
                resolverRegistry.register(field.type(), (MongoValueTypeResolver) ReflectiveMetaData.newInstance(field.resolver().value()));
            }
        }
    }

    public Document applyConditions(Document document) {
        for (MongoCondition condition : conditions) {
            String field = condition.field();
            Object value = document.get(field);

            if (value != null && evaluateCondition(value, condition.expression())) continue;

            throw new IllegalArgumentException("Condition failed for " + field + ": " + condition.expression());
        }
        return document;
    }

    public void enforceConstraints(MongoRepositoryAdapter<?> adapter, Document document) {
        for (String field : uniqueFields) {
            if (adapter.isValueExists(field, document.get(field))) {
                throw new IllegalArgumentException("Unique constraint violated for field: " + field);
            }
        }
        for (String field : nonNullFields) {
            if (document.get(field) == null) {
                throw new IllegalArgumentException("NonNull constraint violated for field: " + field);
            }
        }
    }

    private boolean evaluateCondition(Object value, @NotNull String expression) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Condition evaluation requires a numeric field.");
        }

        String[] parts = expression.trim().split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid condition format: " + expression);
        }

        String operator = parts[1];
        String rawValue = parts[2];

        Number compareValue;
        if (rawValue.endsWith("L")) {
            compareValue = Long.parseLong(rawValue.substring(0, rawValue.length() - 1));
        } else if (rawValue.contains(".")) {
            compareValue = Double.parseDouble(rawValue);
        } else {
            compareValue = Integer.parseInt(rawValue);
        }

        // Use the appropriate comparison method based on the types
        if (value instanceof Integer intValue) {
            return compare(intValue, compareValue.intValue(), operator);
        } else if (value instanceof Long longValue) {
            return compare(longValue, compareValue.longValue(), operator);
        } else if (value instanceof Double doubleValue) {
            return compare(doubleValue, compareValue.doubleValue(), operator);
        } else if (value instanceof Float floatValue) {
            return compare(floatValue, compareValue.floatValue(), operator);
        } else {
            throw new IllegalArgumentException("Unsupported number type: " + value.getClass().getName());
        }
    }

    // Generic comparison method for different numeric types
    private static <T extends Number & Comparable<T>> boolean compare(T value, T compareValue, String operator) {
        return switch (operator) {
            case ">"  -> value.compareTo(compareValue) > 0;
            case ">=" -> value.compareTo(compareValue) >= 0;
            case "<"  -> value.compareTo(compareValue) < 0;
            case "<=" -> value.compareTo(compareValue) <= 0;
            case "==" -> value.compareTo(compareValue) == 0;
            case "!=" -> value.compareTo(compareValue) != 0;
            default   -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }


    public String getCollectionName() {
        return collectionName;
    }
}