import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.query.SqlConditionBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteJsonFilterTest {

    @Test
    void json_select_option_is_not_supported_for_sqlite() {
        RepositoryModel<SqliteJsonEntity, String> model = io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(SqliteJsonEntity.class);

        SqlConditionBuilder<SqliteJsonEntity, String> builder =
            new SqlConditionBuilder<>(QueryParseEngine.SQLType.SQLITE, model);

        List<FilterOption> options = List.of(
            new JsonSelectOption("payload", "$.profile.name", "=", "Flow")
        );

        assertThrows(UnsupportedOperationException.class, () -> builder.buildConditions(options));
    }

    @Repository(name = "sqlite-json-entity")
    public static class SqliteJsonEntity {
        @Id
        private String id;

        @JsonField
        private String payload;

        public SqliteJsonEntity() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }
}
