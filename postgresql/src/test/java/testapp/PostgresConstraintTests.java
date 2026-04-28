package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.exceptions.InsertRepositoryException;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class PostgresConstraintTests {

    private PostgreSQLRepositoryAdapter<ConstraintEntity, Long> adapter() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        PostgreSQLRepositoryAdapter<ConstraintEntity, Long> adapter =
            PostgreSQLRepositoryAdapter.builder(ConstraintEntity.class, Long.class)
                .withCredentials(credentials)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS constraint_entity CASCADE;");
        adapter.createRepository(true);

        return adapter;
    }

    @Test
    void nonnull_should_fail() {
        var adapter = adapter();

        ConstraintEntity e = new ConstraintEntity();
        e.setName(null);

        assertThrows(InsertRepositoryException.class, () -> adapter.insert(e).get());
    }

    @Test
    void unique_should_fail_on_duplicate() {
        var adapter = adapter();

        ConstraintEntity e1 = new ConstraintEntity();
        e1.setName("same");

        ConstraintEntity e2 = new ConstraintEntity();
        e2.setName("same");

        adapter.insert(e1);

        assertThrows(InsertRepositoryException.class, () -> adapter.insert(e2).get());
    }
}