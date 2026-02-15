package io.github.flameyossnowy.universal.api.options;

public sealed interface FilterOption
    permits AggregateFilterOption, JsonSelectOption, SelectOption {
}
