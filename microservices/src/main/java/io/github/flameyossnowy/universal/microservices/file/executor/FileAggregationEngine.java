package io.github.flameyossnowy.universal.microservices.file.executor;

import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.*;
import io.github.flameyossnowy.uniform.json.JsonAdapter;
import io.github.flameyossnowy.uniform.json.dom.JsonArray;
import io.github.flameyossnowy.uniform.json.dom.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Evaluates {@link AggregationQuery} and {@link WindowQuery} operations entirely
 * in-memory. Has no I/O dependencies; it only operates on pre-fetched entity lists
 * supplied by the caller.
 */
public class FileAggregationEngine<T, ID> {

    private final RepositoryModel<T, ID> repositoryModel;
    private final JsonAdapter objectMapper;
    private final FileFilterEngine<T, ID> filterEngine;
    private final FileQueryExecutor<T, ID> queryExecutor;

    public FileAggregationEngine(
            @NotNull RepositoryModel<T, ID> repositoryModel,
            @NotNull JsonAdapter objectMapper,
            @NotNull FileFilterEngine<T, ID> filterEngine,
            @NotNull FileQueryExecutor<T, ID> queryExecutor
    ) {
        this.repositoryModel = repositoryModel;
        this.objectMapper    = objectMapper;
        this.filterEngine    = filterEngine;
        this.queryExecutor   = queryExecutor;
    }

    // -------------------------------------------------------------------------
    // Aggregation
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> aggregate(@NotNull AggregationQuery query, @NotNull List<T> base) {
        if (query.limit() == 0) return Collections.emptyList();

        List<String> groupBy = query.groupByFields() == null ? Collections.emptyList() : query.groupByFields();
        Map<List<Object>, List<T>> groups = groupEntities(base, groupBy);

        List<Map<String, Object>> rows = new ArrayList<>(groups.size());
        for (List<T> group : groups.values()) {
            Map<String, Object> row = buildAggregationRow(query.selectFields(), group);
            if (matchesHaving(row, group, query.havingFilters())) {
                rows.add(row);
            }
        }

        if (query.orderBy() != null && !query.orderBy().isEmpty()) {
            rows.sort(buildMapComparator(query.orderBy()));
        }

        if (query.limit() >= 0 && rows.size() > query.limit()) {
            return rows.subList(0, query.limit());
        }
        return rows;
    }

    // -------------------------------------------------------------------------
    // Window functions
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> window(@NotNull WindowQuery query, @NotNull List<T> base) {
        if (query.limit() == 0) return Collections.emptyList();

        List<Map<String, Object>> rows = prepareWindowRows(query.selectFields(), base);

        for (FieldDefinition fd : query.selectFields()) {
            if (fd instanceof WindowFieldDefinition w) {
                applyWindowFunction(rows, w);
            }
        }

        // Remove internal entity backrefs
        for (Map<String, Object> row : rows) {
            row.remove("__entity");
        }

        if (query.limit() >= 0 && rows.size() > query.limit()) {
            return rows.subList(0, query.limit());
        }
        return rows;
    }

    // -------------------------------------------------------------------------
    // Private – grouping
    // -------------------------------------------------------------------------

    private Map<List<Object>, List<T>> groupEntities(List<T> base, List<String> groupBy) {
        Map<List<Object>, List<T>> groups = new LinkedHashMap<>(32);
        List<Object> key = new ArrayList<>(groupBy.size());

        for (T entity : base) {
            for (String field : groupBy) {
                var fm = repositoryModel.fieldByName(field);
                key.add(fm != null ? fm.getValue(entity) : null);
            }
            groups.computeIfAbsent(new ArrayList<>(key), ignored -> new ArrayList<>(16)).add(entity);
            key.clear();
        }
        return groups;
    }

    // -------------------------------------------------------------------------
    // Private – aggregation row building
    // -------------------------------------------------------------------------

    private @NotNull Map<String, Object> buildAggregationRow(
            @NotNull List<FieldDefinition> fields,
            @NotNull List<T> group
    ) {
        Map<String, Object> row = new LinkedHashMap<>(fields.size());
        T first = group.getFirst();

        for (FieldDefinition fd : fields) {
            switch (fd) {
                case SimpleFieldDefinition s -> {
                    var fm = repositoryModel.fieldByName(s.field());
                    row.put(s.getFieldName(), fm != null ? fm.getValue(first) : null);
                }
                case QueryField<?> q -> {
                    var fm = repositoryModel.fieldByName(q.getFieldName());
                    row.put(q.getFieldName(), fm != null ? fm.getValue(first) : null);
                }
                case AggregateFieldDefinition a -> row.put(a.alias(), computeAggregate(a, group));
                case SubQuery.SubQueryFieldDefinition ignored ->
                    throw new UnsupportedOperationException(
                        "Scalar subqueries in SELECT are not supported by file aggregation yet");
                case WindowFieldDefinition w -> throw new UnsupportedOperationException(
                    "Window fields are not valid in AggregationQuery: " + w.alias());
                default -> throw new UnsupportedOperationException(
                    "Unsupported field definition: " + fd.getClass().getName());
            }
        }
        return row;
    }

    // -------------------------------------------------------------------------
    // Private – aggregate computation
    // -------------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object computeAggregate(@NotNull AggregateFieldDefinition a, @NotNull List<T> group) {
        Function<T, Object> extractor = entity -> {
            var fm = repositoryModel.fieldByName(a.field());
            Object base = fm != null ? fm.getValue(entity) : null;
            if (a.isJson()) {
                JsonObject root     = objectMapper.valueToTree(base);
                JsonObject selected = FileFilterEngine.selectJsonPath(root, a.jsonPath());
                return selected == null ? null : objectMapper.treeToValue(selected, Object.class);
            }
            return base;
        };

        return switch (a.aggregationType()) {
            case COUNT          -> (long) group.size();
            case COUNT_DISTINCT -> countDistinct(group, extractor);
            case MIN            -> computeMin(group, extractor);
            case MAX            -> computeMax(group, extractor);
            case SUM            -> sumNumbers(group, extractor);
            case AVG            -> avgNumbers(group, extractor);
            case COUNT_IF       -> countIf(group, a.field(), a.condition());
            case SUM_IF         -> sumIf(group, a.field(), a.condition(), extractor);
            case ARRAY_LENGTH   -> arrayLength(group, extractor);
            default             -> throw new UnsupportedOperationException(
                "Aggregation not supported in file adapter: " + a.aggregationType());
        };
    }

    private long countDistinct(List<T> group, Function<T, Object> extractor) {
        long count = 0L;
        Set<Object> seen = new HashSet<>(group.size());
        for (T t : group) {
            Object o = extractor.apply(t);
            if (o != null && seen.add(o)) count++;
        }
        return count;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable Object computeMin(List<T> group, Function<T, Object> extractor) {
        boolean seen = false;
        Object best = null;
        for (T t : group) {
            Object o = extractor.apply(t);
            if (o != null && (!seen || ((Comparable) o).compareTo(best) < 0)) { seen = true; best = o; }
        }
        return seen ? best : null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable Object computeMax(List<T> group, Function<T, Object> extractor) {
        boolean seen = false;
        Object best = null;
        for (T t : group) {
            Object o = extractor.apply(t);
            if (o != null && (!seen || ((Comparable) o).compareTo(best) > 0)) { seen = true; best = o; }
        }
        return seen ? best : null;
    }

    private @Nullable Double sumNumbers(List<T> group, Function<T, Object> extractor) {
        double sum = 0d;
        boolean seen = false;
        for (T e : group) {
            Object v = extractor.apply(e);
            if (v instanceof Number n) { sum += n.doubleValue(); seen = true; }
        }
        return seen ? sum : null;
    }

    private @Nullable Double avgNumbers(List<T> group, Function<T, Object> extractor) {
        double sum = 0d;
        long count = 0;
        for (T e : group) {
            Object v = extractor.apply(e);
            if (v instanceof Number n) { sum += n.doubleValue(); count++; }
        }
        return count == 0 ? null : (sum / count);
    }

    private long countIf(@NotNull List<T> group, @NotNull String field, @Nullable FilterOption condition) {
        if (condition == null) return group.size();

        long count = 0L;
        for (T e : group) {
            FilterOption effective = resolveEffectiveFilter(field, condition);
            if (filterEngine.matches(e, effective)) count++;
        }
        return count;
    }

    private @Nullable Double sumIf(
            @NotNull List<T> group,
            @NotNull String field,
            @Nullable FilterOption condition,
            Function<T, Object> extractor
    ) {
        if (condition == null) return sumNumbers(group, extractor);

        double sum = 0d;
        boolean seen = false;
        for (T e : group) {
            FilterOption effective = resolveEffectiveFilter(field, condition);
            if (!filterEngine.matches(e, effective)) continue;

            Object v = extractor.apply(e);
            if (v instanceof Number n) { sum += n.doubleValue(); seen = true; }
        }
        return seen ? sum : null;
    }

    /**
     * The DSL helper {@code Query.eq(value)} builds a {@link SelectOption} with an empty
     * option field, meaning "apply this operator to the field from the surrounding context".
     * We rewrite such anonymous filters to attach the correct field name.
     */
    private static FilterOption resolveEffectiveFilter(String contextField, FilterOption condition) {
        if (condition instanceof SelectOption(String option, String operator, Object value)
                && (option == null || option.isBlank())) {
            return new SelectOption(contextField, operator, value);
        }
        return condition;
    }

    private @Nullable Integer arrayLength(List<T> group, Function<T, Object> extractor) {
        for (T e : group) {
            Object v = extractor.apply(e);
            if (v instanceof Collection<?> c)                    return c.size();
            if (v != null && v.getClass().isArray())             return java.lang.reflect.Array.getLength(v);
            if (v instanceof JsonArray node)                     return node.size();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Private – HAVING evaluation
    // -------------------------------------------------------------------------

    private boolean matchesHaving(
            @NotNull Map<String, Object> row,
            @NotNull List<T> group,
            @Nullable List<FilterOption> filters
    ) {
        if (filters == null || filters.isEmpty()) return true;
        for (FilterOption f : filters) {
            if (!matchesHavingRow(row, group, f)) return false;
        }
        return true;
    }

    private boolean matchesHavingRow(
            @NotNull Map<String, Object> row,
            @NotNull List<T> group,
            @NotNull FilterOption filter
    ) {
        if (filter instanceof SelectOption(String option, String operator, Object value)) {
            return matchesAggregatedValue(row.get(option), operator, value);
        }

        if (filter instanceof AggregateFilterOption(
            String field, String jsonPath, String operator, Object value,
            AggregationType aggregationType, FilterOption condition, String alias
        )) {
            Object actual = (alias != null && !alias.isBlank())
                ? row.get(alias)
                : computeAggregate(new AggregateFieldDefinition(field, jsonPath, aggregationType, condition, "__having"), group);
            return matchesAggregatedValue(actual, operator, value);
        }

        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matchesAggregatedValue(
            @Nullable Object actual,
            @NotNull String operator,
            @Nullable Object expected
    ) {
        if (actual == null) return expected == null;

        if (actual instanceof Number an && expected instanceof Number en) {
            double a = an.doubleValue(), e = en.doubleValue();
            return switch (operator) {
                case "="  -> Double.compare(a, e) == 0;
                case "!=" -> Double.compare(a, e) != 0;
                case ">"  -> a > e;
                case "<"  -> a < e;
                case ">=" -> a >= e;
                case "<=" -> a <= e;
                default   -> false;
            };
        }

        return switch (operator) {
            case "="  -> actual.equals(expected);
            case "!=" -> !actual.equals(expected);
            case ">"  -> actual instanceof Comparable c && expected != null && c.compareTo(expected) > 0;
            case "<"  -> actual instanceof Comparable c && expected != null && c.compareTo(expected) < 0;
            case ">=" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) >= 0;
            case "<=" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) <= 0;
            case "IN" -> expected instanceof Collection<?> list && list.contains(actual);
            default   -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Private – window function support
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> prepareWindowRows(
            @NotNull List<FieldDefinition> fields,
            @NotNull List<T> base
    ) {
        List<Map<String, Object>> rows = new ArrayList<>(base.size());
        for (T e : base) {
            Map<String, Object> row = new LinkedHashMap<>(fields.size() + 1);
            for (FieldDefinition fd : fields) {
                if (fd instanceof SimpleFieldDefinition s) {
                    var fm = repositoryModel.fieldByName(s.field());
                    row.put(s.getFieldName(), fm != null ? fm.getValue(e) : null);
                } else if (fd instanceof WindowFieldDefinition w) {
                    row.put(w.alias(), null); // filled later
                } else {
                    throw new UnsupportedOperationException(
                        "Unsupported field in WindowQuery: " + fd.getClass().getName());
                }
            }
            row.put("__entity", e);
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyWindowFunction(
            @NotNull List<Map<String, Object>> rows,
            @NotNull WindowFieldDefinition w
    ) {
        // Partition
        int partSize = w.partitionBy() == null ? 16 : w.partitionBy().size();
        Map<List<Object>, List<Map<String, Object>>> partitions = new LinkedHashMap<>(partSize);

        for (Map<String, Object> row : rows) {
            T e = (T) row.get("__entity");
            List<Object> key = new ArrayList<>(partSize);
            if (w.partitionBy() != null) {
                for (String p : w.partitionBy()) {
                    var fm = repositoryModel.fieldByName(p);
                    key.add(fm != null ? fm.getValue(e) : null);
                }
            }
            partitions.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        for (List<Map<String, Object>> part : partitions.values()) {
            // Sort within partition
            if (w.orderBy() != null && !w.orderBy().isEmpty()) {
                part.sort(buildWindowComparator(w));
            }

            switch (w.functionType()) {
                case ROW_NUMBER -> {
                    for (int i = 0; i < part.size(); i++) part.get(i).put(w.alias(), i + 1L);
                }
                case RANK -> {
                    long rank = 1;
                    for (int i = 0; i < part.size(); i++) {
                        if (i > 0 && differentOrdering(part.get(i - 1), part.get(i), w.orderBy())) rank = i + 1L;
                        part.get(i).put(w.alias(), rank);
                    }
                }
                case DENSE_RANK -> {
                    long rank = 1;
                    for (int i = 0; i < part.size(); i++) {
                        if (i > 0 && differentOrdering(part.get(i - 1), part.get(i), w.orderBy())) rank++;
                        part.get(i).put(w.alias(), rank);
                    }
                }
                case COUNT -> {
                    long running = 0;
                    for (Map<String, Object> m : part) m.put(w.alias(), ++running);
                }
                case SUM, AVG, MIN, MAX -> computeRollingNumeric(part, w);
                default -> throw new UnsupportedOperationException(
                    "Window function not supported in file adapter: " + w.functionType());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void computeRollingNumeric(
            @NotNull List<Map<String, Object>> part,
            @NotNull WindowFieldDefinition w
    ) {
        double runningSum   = 0d;
        long runningCount   = 0;
        Comparable runningMin = null;
        Comparable runningMax = null;

        for (Map<String, Object> m : part) {
            T e  = (T) m.get("__entity");
            var fm = repositoryModel.fieldByName(w.field());
            Object v = fm != null ? fm.getValue(e) : null;

            if (v instanceof Number n)     { runningSum += n.doubleValue(); runningCount++; }
            if (v instanceof Comparable c) {
                runningMin = runningMin == null ? c : (runningMin.compareTo(c) <= 0 ? runningMin : c);
                runningMax = runningMax == null ? c : (runningMax.compareTo(c) >= 0 ? runningMax : c);
            }

            Object out = switch (w.functionType()) {
                case SUM -> runningCount == 0 ? null : runningSum;
                case AVG -> runningCount == 0 ? null : (runningSum / runningCount);
                case MIN -> runningMin;
                case MAX -> runningMax;
                default  -> null;
            };
            m.put(w.alias(), out);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable Comparator<Map<String, Object>> buildWindowComparator(@NotNull WindowFieldDefinition w) {
        Comparator<Map<String, Object>> cmp = null;
        for (SortOption s : w.orderBy()) {
            Comparator<Map<String, Object>> next = Comparator.comparing(m -> {
                T e  = (T) m.get("__entity");
                var fm = repositoryModel.fieldByName(s.field());
                return (Comparable) (fm != null ? fm.getValue(e) : null);
            }, Comparator.nullsFirst(Comparator.naturalOrder()));
            if (s.order() == SortOrder.DESCENDING) next = next.reversed();
            cmp = cmp == null ? next : cmp.thenComparing(next);
        }
        return cmp;
    }

    @SuppressWarnings({"unchecked"})
    private boolean differentOrdering(
            @NotNull Map<String, Object> a,
            @NotNull Map<String, Object> b,
            @Nullable List<SortOption> order
    ) {
        if (order == null || order.isEmpty()) return false;
        T ea = (T) a.get("__entity");
        T eb = (T) b.get("__entity");
        for (SortOption s : order) {
            var fm = repositoryModel.fieldByName(s.field());
            Object va = fm != null ? fm.getValue(ea) : null;
            Object vb = fm != null ? fm.getValue(eb) : null;
            if (!Objects.equals(va, vb)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Shared comparator for map rows (ORDER BY in aggregate queries)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static @Nullable Comparator<Map<String, Object>> buildMapComparator(@NotNull List<SortOption> orderBy) {
        Comparator<Map<String, Object>> comparator = null;
        for (SortOption sort : orderBy) {
            Comparator<Map<String, Object>> next = Comparator.comparing(
                m -> (Comparable<Object>) m.get(sort.field()),
                Comparator.nullsFirst(Comparator.naturalOrder())
            );
            if (sort.order() == SortOrder.DESCENDING) next = next.reversed();
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator;
    }
}