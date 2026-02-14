package io.github.flameyossnowy.universal.api.options;

public record JsonSelectOption(
    String field,        // entity field name (NOT column name)
    String jsonPath,     // $.email, $.profile.name, etc
    String operator,     // =, !=, @>, etc (adapter validated)
    Object value
) implements FilterOption {}
