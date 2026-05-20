package io.github.flameyossnowy.universal.checker.schema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates queries against repository schemas.
 * Uses a ValidationReporter to emit errors and warnings instead of throwing exceptions.
 */
public final class QueryValidator {

    private final SchemaRegistry registry;
    private final ValidationReporter reporter;

    public QueryValidator(SchemaRegistry registry, ValidationReporter reporter) {
        this.registry = registry;
        this.reporter = reporter;
    }

    /**
     * Validates a query model against its repository schema.
     *
     * @param query the query model to validate
     */
    public void validate(QueryModel query) {
        RepositorySchema schema = registry.get(query.repository());
        if (schema == null) {
            reporter.error("Unknown repository: " + query.repository());
            return;
        }

        // Check for overly broad queries (no filters and no ordering)
        if (query.conditions().isEmpty() && query.orderBy().isEmpty()) {
            reporter.error("Query is too broad (no filters or ordering)");
        }

        // Check for conflicting conditions
        if (conditionsHasConflict(query.conditions())) {
            reporter.error("Query contains conflicting conditions");
        }

        validateConditions(schema, query.conditions());
        validateOrderBy(schema, query.orderBy());
    }

    private void validateConditions(RepositorySchema schema, List<Condition> conditions) {
        for (Condition c : conditions) {
            FieldSchema field = schema.field(c.field());

            // existence already enforced by schema.field()
            validateConditionType(schema, field, c);
        }
    }

    private void validateOrderBy(RepositorySchema schema, List<OrderBy> orderBy) {
        for (OrderBy o : orderBy) {
            schema.field(o.field()); // ensures existence
        }
    }

    private void validateConditionType(
        RepositorySchema schema,
        FieldSchema field,
        Condition condition
    ) {
        FieldType ft = field.fieldType();

        switch (condition.operator()) {

            case EQ, NEQ -> {
                validateEqualityCompatible(ft, condition.value(), field);
            }

            case GT, GTE, LT, LTE -> {
                validateComparable(field, condition);
            }

            case LIKE -> {
                if (ft != FieldType.STRING) {
                    reporter.error(field, "LIKE can only be used on STRING fields");
                }
            }

            case IN -> {
                if (ft == FieldType.BOOLEAN || ft == FieldType.UUID) {
                    // allowed but usually suspicious
                    reporter.warn(field, "IN on " + ft + " may be inefficient");
                }
            }

            case IS_NULL -> {
                // always valid
            }
        }
    }

    private void validateComparable(FieldSchema field, Condition condition) {
        FieldType ft = field.fieldType();
        Object value = condition.value();

        // Case 1: field OP literal
        if (!(value instanceof String s && s.contains("."))) {

            if (ft == FieldType.STRING || ft == FieldType.BOOLEAN || ft == FieldType.UUID) {
                reporter.error(field,
                    "Cannot use comparison operators (>, <, >=, <=) on type: " + ft);
                return;
            }

            return;
        }

        // Case 2: field OP field2 (cross-field comparison)
        String otherFieldName = value.toString();

        FieldType otherType = resolveTypeUnsafe(field, otherFieldName);

        if (!isComparablePair(ft, otherType)) {
            reporter.error(field,
                "Invalid field comparison: " + ft + " vs " + otherType);
        }
    }

    private boolean isComparablePair(FieldType a, FieldType b) {
        if (a == FieldType.UNKNOWN || b == FieldType.UNKNOWN) return false;

        if (a == FieldType.NUMBER && b == FieldType.NUMBER) return true;

        return a == FieldType.TEMPORAL && b == FieldType.TEMPORAL;

        // explicitly disallow everything else
    }

    private void validateEqualityCompatible(FieldType ft, Object value, FieldSchema field) {
        if (value == null) return;

        switch (ft) {
            case STRING, UUID, ENUM -> {
                // always OK
            }
            case NUMBER -> {
                if (!(value instanceof Number || value instanceof String)) {
                    reporter.error(field, "Invalid number comparison value");
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    reporter.error(field, "Invalid boolean comparison value");
                }
            }
            case TEMPORAL -> {
                // accept ISO string or numeric timestamp
            }
            default -> {
                // JSON / UNKNOWN
                reporter.error(field, "Cannot compare unsupported type: " + ft);
            }
        }
    }

    private boolean conditionsHasConflict(List<Condition> conditions) {
        // naive but effective:
        Map<String, Set<Condition.Operator>> map = new HashMap<>();

        for (Condition c : conditions) {
            map.computeIfAbsent(c.field(), k -> new HashSet<>())
                .add(c.operator());
        }

        // Check for conflicting operators on the same field
        for (Map.Entry<String, Set<Condition.Operator>> entry : map.entrySet()) {
            Set<Condition.Operator> ops = entry.getValue();

            // EQ and NEQ on same field is a conflict
            if (ops.contains(Condition.Operator.EQ) && ops.contains(Condition.Operator.NEQ)) {
                return true;
            }

            // GT and LTE on same field is a conflict
            if (ops.contains(Condition.Operator.GT) && ops.contains(Condition.Operator.LTE)) {
                return true;
            }

            // GTE and LT on same field is a conflict
            if (ops.contains(Condition.Operator.GTE) && ops.contains(Condition.Operator.LT)) {
                return true;
            }
        }

        return false;
    }

    private FieldType resolveTypeUnsafe(FieldSchema field, String otherFieldName) {
        // This would need access to the schema to resolve field types
        // For now, return UNKNOWN
        return FieldType.UNKNOWN;
    }
}