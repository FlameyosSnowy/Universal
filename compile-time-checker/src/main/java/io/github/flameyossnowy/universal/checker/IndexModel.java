package io.github.flameyossnowy.universal.checker;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.util.List;

public record IndexModel(
    String name,
    List<String> fields,
    IndexType type
) {}