import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.query.SqlConditionBuilder;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlParameterBinder;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresJsonFilterTest {

    @Test
    void json_select_option_builds_postgres_json_path_condition() {
        ModelsBootstrap.init();
        RepositoryModel<PostgresJsonEntity, String> model = io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(PostgresJsonEntity.class);

        SqlConditionBuilder<PostgresJsonEntity, String> builder =
            new SqlConditionBuilder<>(QueryParseEngine.SQLType.POSTGRESQL, model);

        List<FilterOption> options = List.of(
            new JsonSelectOption("payload", "$.profile.name", "=", "Flow")
        );

        String where = builder.buildConditions(options);
        assertEquals("payload #>> '{profile,name}' = ?", where);
    }

    @Test
    void json_select_option_whole_object_uses_jsonCodec() {
        ModelsBootstrap.init();
        RepositoryModel<PostgresJsonEntity, String> model = io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(PostgresJsonEntity.class);

        TypeResolverRegistry registry = new TypeResolverRegistry();

        String sql = "SELECT * FROM postgres_json_entity WHERE payload #>> '{profile}' = ?";

        CapturingParameters params = new CapturingParameters(
            mock(PreparedStatement.class),
            registry,
            sql,
            model
        );

        SqlParameterBinder<PostgresJsonEntity, String> binder = new SqlParameterBinder<>();

        List<FilterOption> filters = List.of(
            new JsonSelectOption(
                "payload",
                "$.profile",
                "=",
                new PostgresJsonEntity.Payload("Flow", 21)
            )
        );

        binder.addFilterToPreparedStatement(filters, params, registry, model, QueryParseEngine.SQLType.POSTGRESQL);

        assertEquals("payload #>> '{profile}'", params.lastName);
        assertEquals("{\"n\":\"Flow\",\"a\":21}", params.lastValue);
    }

    private static final class CapturingParameters extends SQLDatabaseParameters {
        private String lastName;
        private Object lastValue;

        CapturingParameters(
            PreparedStatement statement,
            TypeResolverRegistry registry,
            String sql,
            RepositoryModel<?, ?> model
        ) {
            super(statement, registry, sql, model, mock(io.github.flameyossnowy.universal.api.handler.CollectionHandler.class), false);
        }

        @Override
        public <T> void setRaw(String name, T value, Class<?> type) {
            this.lastName = name;
            this.lastValue = value;
        }

        @Override
        public void setNull(String name, Class<?> type) {
            this.lastName = name;
            this.lastValue = null;
        }
    }
}
