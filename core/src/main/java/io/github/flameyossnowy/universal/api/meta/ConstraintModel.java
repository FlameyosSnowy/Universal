package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.annotations.Constraint;

import java.util.List;

public interface ConstraintModel {
    String name();

    List<String> fields();

    /**
     * Returns the constraint type (e.g., ORDERED, UNIQUE_COMBINATION, etc.)
     *
     * @return the constraint type
     */
    Constraint.Type type();
}
