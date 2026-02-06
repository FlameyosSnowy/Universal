package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.util.List;

public interface IndexModel {
    String name();

    List<String> fields();

    IndexType type();
}
