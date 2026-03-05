package io.github.flameyossnowy.universal.microservices.file.executor;

import io.github.flameyossnowy.universal.api.ReadPolicy;
import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.*;
import me.flame.uniform.json.JsonAdapter;
import me.flame.uniform.json.dom.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Evaluates filter predicates against in-memory entities.
 * Handles {@link SelectOption}, {@link JsonSelectOption}, subqueries, and
 * all comparison operators. Has no I/O dependencies.
 */
public class FileFilterEngine<T, ID> {

    private final RepositoryModel<T, ID> repositoryModel;
    private final JsonAdapter objectMapper;

    public FileFilterEngine(
            @NotNull RepositoryModel<T, ID> repositoryModel,
            @NotNull JsonAdapter objectMapper
    ) {
        this.repositoryModel = repositoryModel;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean matchesAll(T entity, List<FilterOption> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (FilterOption filter : filters) {
            if (!matches(entity, filter)) return false;
        }
        return true;
    }

    public boolean matches(T entity, FilterOption filter) {
        try {
            if (filter instanceof SelectOption s)     return matchesSelectOption(entity, s);
            if (filter instanceof JsonSelectOption j) return matchesJsonSelectOption(entity, j);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // SelectOption matching
    // -------------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean matchesSelectOption(T entity, SelectOption filter) {
        var field = repositoryModel.fieldByName(filter.option());
        if (field == null) return false;

        Object value       = field.getValue(entity);
        Object filterValue = filter.value();
        String operator    = filter.operator();

        if (value == null) return filterValue == null;

        return switch (operator) {
            case "="   -> value.equals(filterValue);
            case "!="  -> !value.equals(filterValue);
            case ">"   -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) > 0;
            case "<"   -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) < 0;
            case ">="  -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) >= 0;
            case "<="  -> value instanceof Comparable && filterValue != null && ((Comparable) value).compareTo(filterValue) <= 0;
            case "IN" -> {
                if (filterValue instanceof SubQuery sq) yield evaluateInSubQuery(entity, filter.option(), sq, false);
                yield filterValue instanceof Collection<?> list && list.contains(value);
            }
            case "NOT IN" -> {
                if (filterValue instanceof SubQuery sq) yield evaluateInSubQuery(entity, filter.option(), sq, true);
                yield !(filterValue instanceof Collection<?> list) || !list.contains(value);
            }
            case "EXISTS"     -> filterValue instanceof SubQuery sq && evaluateExistsSubQuery(sq, false);
            case "NOT EXISTS" -> !(filterValue instanceof SubQuery sq) || evaluateExistsSubQuery(sq, true);
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // JsonSelectOption matching
    // -------------------------------------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean matchesJsonSelectOption(T entity, JsonSelectOption filter) {
        var jsonField = repositoryModel.fieldByName(filter.field());
        if (jsonField == null) return false;

        Object jsonValue = jsonField.getValue(entity);
        if (jsonValue == null) return filter.value() == null;

        JsonObject root     = objectMapper.valueToTree(jsonValue);
        JsonObject selected = selectJsonPath(root, filter.jsonPath());

        if (selected == null || selected.isNull()) {
            return filter.value() == null;
        }

        Object actual   = objectMapper.treeToValue(selected, Object.class);
        Object expected = filter.value();
        String operator = filter.operator();

        if (actual == null) return expected == null;

        return switch (operator) {
            case "="  -> actual.equals(expected);
            case "!=" -> !actual.equals(expected);
            case ">"  -> actual instanceof Comparable c && expected != null && c.compareTo(expected) > 0;
            case "<"  -> actual instanceof Comparable c && expected != null && c.compareTo(expected) < 0;
            case ">=" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) >= 0;
            case "<=" -> actual instanceof Comparable c && expected != null && c.compareTo(expected) <= 0;
            case "IN" -> expected instanceof Collection<?> list && list.contains(actual);
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // JSON path evaluation
    // -------------------------------------------------------------------------

    public static @Nullable JsonObject selectJsonPath(@NotNull JsonObject root, @Nullable String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) return null;
        if (!jsonPath.startsWith("$")) return null;

        if ("$".equals(jsonPath))       return root;
        if (!jsonPath.startsWith("$.")) return null;

        String path    = jsonPath.substring(2);
        JsonObject current = root;
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) return null;
            current = current.contains(part) ? current.getObject(part) : null;
            if (current == null) return null;
        }
        return current;
    }

    // -------------------------------------------------------------------------
    // Subquery evaluation
    // -------------------------------------------------------------------------

    private boolean evaluateInSubQuery(T entity, String localField, @NotNull SubQuery subQuery, boolean negate) {
        List<Object> values = executeSubQuerySingleField(subQuery);
        Object localValue   = repositoryModel.fieldByName(localField).getValue(entity);
        return negate != values.contains(localValue);
    }

    private static boolean evaluateExistsSubQuery(@NotNull SubQuery subQuery, boolean negate) {
        return negate == executeSubQuerySingleField(subQuery).isEmpty();
    }

    public static @NotNull List<Object> executeSubQuerySingleField(@NotNull SubQuery subQuery) {
        @SuppressWarnings("unchecked")
        RepositoryAdapter<Object, Object, ?> adapter =
            (RepositoryAdapter<Object, Object, ?>) RepositoryRegistry.get(subQuery.entityClass());

        if (adapter == null) {
            throw new IllegalStateException(
                "No adapter registered for subquery entity: " + subQuery.entityClass().getName());
        }

        if (subQuery.selectFields().size() != 1) {
            throw new UnsupportedOperationException(
                "File subqueries currently require selecting exactly one simple field");
        }

        String selectedField = subQuery.selectFields().getFirst().getFieldName();

        SelectQuery sq = new SelectQuery(
            List.of(selectedField),
            subQuery.whereFilters(),
            subQuery.orderBy(),
            subQuery.limit(),
            null
        );

        List<Object> objects = adapter.find(sq, ReadPolicy.NO_READ_POLICY);
        List<Object> out     = new ArrayList<>(objects.size());

        RepositoryModel<Object, Object> model = adapter.getRepositoryModel();
        FieldModel<Object> fm = model.fieldByName(selectedField);
        if (fm != null) {
            for (Object e : objects) {
                out.add(fm.getValue(e));
            }
        }
        return out;
    }
}