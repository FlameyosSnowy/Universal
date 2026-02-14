package io.github.flameyossnowy.universal.api.options;

import org.jetbrains.annotations.Contract;

import java.util.Collection;

/**
 * Field-scoped operator builder.
 *
 * <p>Ensures operators are attached to a concrete field and prevents
 * malformed filter construction.</p>
 */
@SuppressWarnings("unused")
public class QueryField<B extends Filterable> {
    private final B builder;
    private final String field;
    private final String jsonPath;

    @Contract(pure = true)
    QueryField(B builder, String field) {
        this(builder, field, null);
    }

    @Contract(pure = true)
    QueryField(B builder, String field, String jsonPath) {
        this.builder = builder;
        this.field = field;
        this.jsonPath = jsonPath;
    }

    private B add(String operator, Object value) {
        FilterOption filter;
        if (jsonPath != null) {
            filter = new JsonSelectOption(field, jsonPath, operator, value);
        } else {
            filter = new SelectOption(field, operator, value);
        }
        builder.addFilter(filter);
        return builder;
    }

    public B eq(Object value) { return add("=", value); }

    public B ne(Object value) { return add("!=", value); }

    public B gt(Object value) { return add(">", value); }

    public B gte(Object value) { return add(">=", value); }

    public B lt(Object value) { return add("<", value); }

    public B lte(Object value) { return add("<=", value); }

    public B in(Collection<?> values) { return add("IN", values); }

    public B like(String pattern) { return add("LIKE", pattern); }

    public B not() { return add("NOT", null); }

    public B and() { return add("AND", null); }

    public B or() { return add("OR", null); }
}