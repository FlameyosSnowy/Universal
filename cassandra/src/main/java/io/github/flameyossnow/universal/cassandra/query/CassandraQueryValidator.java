package io.github.flameyossnow.universal.cassandra.query;

import io.github.flameyossnowy.universal.api.options.DeleteQuery;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.api.options.validator.QueryValidator;
import io.github.flameyossnowy.universal.api.options.validator.ValidationEstimation;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;

public record CassandraQueryValidator(RepositoryInformation repositoryInformation) implements QueryValidator {

    @Override
    public ValidationEstimation validateSelectQuery(SelectQuery query) {
        if (query.limit() < 1) {
            return ValidationEstimation.fail("Limit must be greater than 0");
        }
        for (SelectOption filter : query.filters()) {
            FieldData<?> field = repositoryInformation.getField(filter.option());
            if (field == null) return ValidationEstimation.fail("Filter option " + filter.option() + " does not exist");
            if (!field.primary())
                return ValidationEstimation.fail("Filter option " + filter.option() + " cannot be filtered on in cassandra.");
        }
        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateDeleteQuery(DeleteQuery query) {
        return ValidationEstimation.PASS;
    }

    @Override
    public ValidationEstimation validateUpdateQuery(UpdateQuery query) {
        return ValidationEstimation.PASS;
    }
}
