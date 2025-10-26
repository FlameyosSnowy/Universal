package io.github.flameyossnowy.universal.api.options.validator;

import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;

public interface QueryValidator {
    ValidationEstimation validateSelectQuery(SelectQuery query);

    ValidationEstimation validateDeleteQuery(DeleteQuery query);

    ValidationEstimation validateUpdateQuery(UpdateQuery query);
}
