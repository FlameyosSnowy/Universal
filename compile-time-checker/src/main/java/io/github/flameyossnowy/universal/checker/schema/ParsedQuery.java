package io.github.flameyossnowy.universal.checker.schema;

import java.util.List;

public record ParsedQuery(
    List<ParsedCondition> conditions,
    List<ParsedOrder> orderBy,
    Integer limit,
    Integer offset
) {}