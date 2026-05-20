package io.github.flameyossnowy.universal.checker.schema;

import java.util.List;

public record QueryModel(
    String repository,
    List<Condition> conditions,
    List<OrderBy> orderBy,
    Integer limit,
    Integer offset
) {}