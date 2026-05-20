package io.github.flameyossnowy.universal.checker.schema;

import java.util.*;

/**
 * Scans parsed queries and validates them against repository schemas.
 * Uses a ValidationReporter to emit errors and warnings instead of throwing exceptions.
 */
public final class QueryScanner {

    private final SchemaRegistry schemaRegistry;
    private final ValidationReporter reporter;

    public QueryScanner(SchemaRegistry schemaRegistry, ValidationReporter reporter) {
        this.schemaRegistry = schemaRegistry;
        this.reporter = reporter;
    }

    /**
     * Scans a parsed query and returns a QueryModel.
     * Reports errors for unknown fields and operators.
     *
     * @param repository the repository name
     * @param parsed the parsed query
     * @return the query model, or null if critical errors occurred
     */
    public QueryModel scan(String repository, ParsedQuery parsed) {

        RepositorySchema schema = schemaRegistry.get(repository);
        if (schema == null) {
            reporter.error("Unknown repository: " + repository);
            return null;
        }

        List<Condition> conditions = computeConditions(parsed, schema);

        List<OrderBy> order = computeOrder(parsed, schema);

        // Check for overly broad queries
        if (conditions.isEmpty() && order.isEmpty()) {
            reporter.error("Query is too broad (no filters or ordering)");
        }

        // Check for conflicting conditions
        if (conditionsHasConflict(conditions)) {
            reporter.error("Query contains conflicting conditions");
        }

        return new QueryModel(
            repository,
            conditions,
            order,
            parsed.limit(),
            parsed.offset()
        );
    }

    private List<Condition> computeConditions(ParsedQuery parsed, RepositorySchema schema) {
        List<Condition> conditions = new ArrayList<>(parsed.conditions().size());
        for (ParsedCondition pc : parsed.conditions()) {

            if (!schema.hasField(pc.field())) {
                reporter.error("Unknown field in query: " + pc.field());
                continue;
            }

            Condition.Operator op = mapOperator(pc.operator());
            if (op == null) {
                reporter.error("Unknown operator: " + pc.operator());
                continue;
            }

            conditions.add(new Condition(
                pc.field(),
                op,
                pc.value()
            ));
        }
        return conditions;
    }

    private List<OrderBy> computeOrder(ParsedQuery parsed, RepositorySchema schema) {
        List<OrderBy> order = new ArrayList<>(parsed.orderBy().size());
        for (ParsedOrder po : parsed.orderBy()) {
            if (!schema.hasField(po.field())) {
                reporter.error("Unknown order field: " + po.field());
                continue;
            }

            order.add(new OrderBy(
                po.field(),
                po.direction() == ParsedOrder.Direction.ASC
                    ? OrderBy.Direction.ASC
                    : OrderBy.Direction.DESC
            ));
        }
        return order;
    }

    private boolean conditionsHasConflict(List<Condition> conditions) {
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

    private static Condition.Operator mapOperator(String op) {
        return switch (op) {
            case "=" -> Condition.Operator.EQ;
            case "!=" -> Condition.Operator.NEQ;
            case ">" -> Condition.Operator.GT;
            case ">=" -> Condition.Operator.GTE;
            case "<" -> Condition.Operator.LT;
            case "<=" -> Condition.Operator.LTE;
            case "LIKE" -> Condition.Operator.LIKE;
            case "IN" -> Condition.Operator.IN;
            case "IS NULL" -> Condition.Operator.IS_NULL;
            default -> null; // Return null for unknown operators, will be reported as error
        };
    }
}